--[[
    Stealth Overhaul
    Client-side vision-cone overlay. Cached world-space samples are
    reprojected each frame onto the per-player drawing layer. Obstacle
    clipping is approximate LosUtil.lineClear sampling, not floor-depth
    geometry. Placeholder look only.

    Copyright (C) 2026 Connor

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
]]

-- Events.OnGameStart: no parameters (docs/api/Events.md). Client-only.
-- LosUtil.lineClear matches ExposureEvaluator.hasLineOfSight
-- (iso/LosUtil.java:21-85, ignoreDoors=false). World-text pass has depth
-- test off (gameStates/IngameState.java:1111-1113).

local SHOW_CONES = true
local INNER_BAND = 0.45
local CONE_MAX_AGE_MS = 400
local CONE_REFRESH_PER_UPDATE = 2
local LOS_STEPS = 8
local MOVE_SQ = 0.15 * 0.15
local LOOK_DOT_MIN = 0.995
local SAMPLE_DISTANCE = 2.0
local HALF_ANGLE_ITERS = 16

-- Prototype overlay colors. Not a visual commitment.
local CONE_INNER = { r = 0.95, g = 0.85, b = 0.20, a = 0.22 }
local CONE_OUTER = { r = 0.95, g = 0.55, b = 0.10, a = 0.10 }
local CONE_LINE = { r = 0.95, g = 0.80, b = 0.25, a = 0.55 }

StealthOverhaul = StealthOverhaul or {}

---@type Vector2|nil
local lookScratch = nil
local cachedRevision = -1
local cachedHalfAngle = 0

---@return boolean
local function isPatchActive()
    if StealthOverhaulAPI == nil then
        return false
    end
    local ok, active = pcall(function()
        return StealthOverhaulAPI.isPatchActive()
    end)
    if not ok then
        print("[StealthOverhaul] isPatchActive() failed: " .. tostring(active))
        return false
    end
    return active == true
end

---@return Vector2|nil
local function lookVector()
    if lookScratch == nil and Vector2 ~= nil then
        lookScratch = Vector2.new()
    end
    return lookScratch
end

---@return LosUtil.TestResults|nil
local function blockedResult()
    if LosUtil == nil or LosUtil.TestResults == nil then
        return nil
    end
    return LosUtil.TestResults.Blocked
end

---@param cell IsoCell
---@param x0 number
---@param y0 number
---@param z0 number
---@param x1 number
---@param y1 number
---@param blocked LosUtil.TestResults|nil
---@return boolean
local function isRayBlocked(cell, x0, y0, z0, x1, y1, blocked)
    if blocked == nil then
        return false
    end
    local z = math.floor(z0)
    local result = LosUtil.lineClear(
        cell,
        math.floor(x0),
        math.floor(y0),
        z,
        math.floor(x1),
        math.floor(y1),
        z,
        false
    )
    return result == blocked
end

---@param cell IsoCell
---@param x0 number
---@param y0 number
---@param z0 number
---@param dx number
---@param dy number
---@param maxRange number
---@param blocked LosUtil.TestResults|nil
---@return number
local function clippedRange(cell, x0, y0, z0, dx, dy, maxRange, blocked)
    local x1 = x0 + dx * maxRange
    local y1 = y0 + dy * maxRange
    if not isRayBlocked(cell, x0, y0, z0, x1, y1, blocked) then
        return maxRange
    end
    local lo = 0
    local hi = maxRange
    for _ = 1, LOS_STEPS do
        local mid = (lo + hi) * 0.5
        if isRayBlocked(cell, x0, y0, z0, x0 + dx * mid, y0 + dy * mid, blocked) then
            hi = mid
        else
            lo = mid
        end
    end
    return lo
end

---@return number
local function coneHalfAngle()
    local revision = StealthOverhaulAPI.getConfigRevision()
    if revision == cachedRevision and cachedHalfAngle > 0 then
        return cachedHalfAngle
    end
    local lo = 0
    local hi = math.pi
    for _ = 1, HALF_ANGLE_ITERS do
        local mid = (lo + hi) * 0.5
        local factor = StealthOverhaulAPI.angleFactor(math.cos(mid), SAMPLE_DISTANCE)
        if factor > 0 then
            lo = mid
        else
            hi = mid
        end
    end
    cachedRevision = revision
    cachedHalfAngle = lo
    return cachedHalfAngle
end

---@param slot StealthOverhaulSlot
---@param zombie IsoZombie
---@param lookX number
---@param lookY number
---@param range number
---@param revision integer
---@return boolean
local function needsConeRefresh(slot, zombie, lookX, lookY, range, revision)
    if not slot.validCone then
        return true
    end
    if slot.revision ~= revision then
        return true
    end
    local now = getTimestampMs()
    if now - slot.lastRefreshMs >= CONE_MAX_AGE_MS then
        return true
    end
    local dx = zombie:getX() - slot.originX
    local dy = zombie:getY() - slot.originY
    if dx * dx + dy * dy > MOVE_SQ then
        return true
    end
    if math.abs(zombie:getZ() - slot.originZ) > 0.01 then
        return true
    end
    local lookDot = lookX * slot.lookX + lookY * slot.lookY
    if lookDot < LOOK_DOT_MIN then
        return true
    end
    if math.abs(range - slot.range) > 0.25 then
        return true
    end
    return false
end

---@param slot StealthOverhaulSlot
---@param player IsoPlayer
---@param now number
local function sampleCone(slot, player, now)
    local zombie = slot.zombie
    if zombie == nil then
        slot.validCone = false
        return
    end
    local look = lookVector()
    if look == nil then
        slot.validCone = false
        return
    end
    zombie:getLookVector(look)
    local lookX = look:getX()
    local lookY = look:getY()
    local lookLen = math.sqrt(lookX * lookX + lookY * lookY)
    if lookLen < 0.0001 then
        slot.validCone = false
        return
    end
    lookX = lookX / lookLen
    lookY = lookY / lookLen

    local range = StealthOverhaulAPI.getPotentialRange(zombie, player)
    if range <= 0 then
        slot.validCone = false
        return
    end
    local revision = StealthOverhaulAPI.getConfigRevision()
    if not needsConeRefresh(slot, zombie, lookX, lookY, range, revision) then
        return
    end

    local cell = getCell()
    if cell == nil then
        slot.validCone = false
        return
    end

    local half = coneHalfAngle()
    local rayCount = StealthOverhaul.rayCount or #slot.wx
    if rayCount < 2 then
        slot.validCone = false
        return
    end
    local originX = zombie:getX()
    local originY = zombie:getY()
    local originZ = zombie:getZ()
    local span = half * 2
    local blocked = blockedResult()
    for i = 1, rayCount do
        local t = (i - 1) / (rayCount - 1)
        local angle = -half + span * t
        local ca = math.cos(angle)
        local sa = math.sin(angle)
        local dx = lookX * ca - lookY * sa
        local dy = lookX * sa + lookY * ca
        local clipped = clippedRange(cell, originX, originY, originZ, dx, dy, range, blocked)
        slot.wx[i] = originX + dx * clipped
        slot.wy[i] = originY + dy * clipped
        slot.innerWx[i] = originX + dx * clipped * INNER_BAND
        slot.innerWy[i] = originY + dy * clipped * INNER_BAND
    end
    slot.originX = originX
    slot.originY = originY
    slot.originZ = originZ
    slot.lookX = lookX
    slot.lookY = lookY
    slot.range = range
    slot.revision = revision
    slot.lastRefreshMs = now
    slot.validCone = true
end

---@param layer StealthOverhaulLayer
---@param player IsoPlayer
---@param now number
function StealthOverhaul.refreshCones(layer, player, now)
    if not SHOW_CONES or not layer.patchActive then
        return
    end
    local cap = StealthOverhaul.displayCap or #layer.slots
    local refreshed = 0
    local start = layer.coneCursor
    if start < 1 or start > cap then
        start = 1
    end
    local index = start
    for _ = 1, cap do
        if refreshed >= CONE_REFRESH_PER_UPDATE then
            break
        end
        local slot = layer.slots[index]
        if slot ~= nil and slot.zombie ~= nil then
            sampleCone(slot, player, now)
            refreshed = refreshed + 1
        end
        index = index + 1
        if index > cap then
            index = 1
        end
    end
    layer.coneCursor = index
end

---@param layer StealthOverhaulLayer
---@param x1 number
---@param y1 number
---@param x2 number
---@param y2 number
---@param x3 number
---@param y3 number
---@param x4 number
---@param y4 number
---@param color table
local function drawBand(layer, x1, y1, x2, y2, x3, y3, x4, y4, color)
    layer:drawPolygon(nil, x1, y1, x2, y2, x3, y3, x4, y4, color.r, color.g, color.b, color.a)
end

---@param layer StealthOverhaulLayer
---@param player IsoPlayer
---@param dx number
---@param dy number
function StealthOverhaul.drawCones(layer, player, dx, dy)
    if not SHOW_CONES or not layer.patchActive then
        return
    end
    local playerIndex = layer.playerIndex
    local cap = StealthOverhaul.displayCap or #layer.slots
    for s = 1, cap do
        local slot = layer.slots[s]
        if slot ~= nil and slot.validCone and slot.zombie ~= nil then
            local ox = isoToScreenX(playerIndex, slot.originX, slot.originY, slot.originZ) + dx
            local oy = isoToScreenY(playerIndex, slot.originX, slot.originY, slot.originZ) + dy
            local rayCount = #slot.wx
            local prevOuterX = nil
            local prevOuterY = nil
            local prevInnerX = nil
            local prevInnerY = nil
            for i = 1, rayCount do
                local outerX = isoToScreenX(playerIndex, slot.wx[i], slot.wy[i], slot.originZ) + dx
                local outerY = isoToScreenY(playerIndex, slot.wx[i], slot.wy[i], slot.originZ) + dy
                local innerX = isoToScreenX(playerIndex, slot.innerWx[i], slot.innerWy[i], slot.originZ) + dx
                local innerY = isoToScreenY(playerIndex, slot.innerWx[i], slot.innerWy[i], slot.originZ) + dy
                if prevOuterX ~= nil then
                    drawBand(
                        layer,
                        ox,
                        oy,
                        prevInnerX,
                        prevInnerY,
                        innerX,
                        innerY,
                        ox,
                        oy,
                        CONE_INNER
                    )
                    drawBand(
                        layer,
                        prevInnerX,
                        prevInnerY,
                        prevOuterX,
                        prevOuterY,
                        outerX,
                        outerY,
                        innerX,
                        innerY,
                        CONE_OUTER
                    )
                    layer:drawLine2(
                        prevOuterX,
                        prevOuterY,
                        outerX,
                        outerY,
                        CONE_LINE.a,
                        CONE_LINE.r,
                        CONE_LINE.g,
                        CONE_LINE.b
                    )
                end
                prevOuterX = outerX
                prevOuterY = outerY
                prevInnerX = innerX
                prevInnerY = innerY
            end
            if prevOuterX ~= nil then
                layer:drawLine2(ox, oy, prevOuterX, prevOuterY, CONE_LINE.a, CONE_LINE.r, CONE_LINE.g, CONE_LINE.b)
                local firstX = isoToScreenX(playerIndex, slot.wx[1], slot.wy[1], slot.originZ) + dx
                local firstY = isoToScreenY(playerIndex, slot.wx[1], slot.wy[1], slot.originZ) + dy
                layer:drawLine2(ox, oy, firstX, firstY, CONE_LINE.a, CONE_LINE.r, CONE_LINE.g, CONE_LINE.b)
            end
        end
    end
end

local function onGameStart()
    if not isPatchActive() then
        local status = "java API missing"
        if StealthOverhaulAPI ~= nil then
            local ok, explained = pcall(function()
                return StealthOverhaulAPI.explainPatchStatus()
            end)
            if ok and explained ~= nil then
                status = explained
            end
        end
        print("[StealthOverhaul] Lua loaded patchActive=false status=" .. tostring(status))
        return
    end
    local thresholdOk, threshold = pcall(function()
        return StealthOverhaulAPI.getDetectionThreshold()
    end)
    local revisionOk, revision = pcall(function()
        return StealthOverhaulAPI.getConfigRevision()
    end)
    print(
        "[StealthOverhaul] Lua loaded patchActive=true threshold="
            .. tostring(thresholdOk and threshold or "error")
            .. " revision="
            .. tostring(revisionOk and revision or "error")
    )
    if getDebug() then
        local ok, explained = pcall(function()
            return StealthOverhaulAPI.explainPatchStatus()
        end)
        if ok then
            print("[StealthOverhaul] " .. tostring(explained))
        end
    end
end

if StealthOverhaul.onGameStartRenderer then
    Events.OnGameStart.Remove(StealthOverhaul.onGameStartRenderer)
end
StealthOverhaul.onGameStartRenderer = onGameStart
Events.OnGameStart.Add(onGameStart)
