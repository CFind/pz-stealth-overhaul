--[[
    Stealth Overhaul
    Client-side awareness indicators. One world-following drawing layer per
    local player reads authoritative pair state from StealthOverhaulAPI.
    Placeholder primitives only; visual style is not committed.

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

require "ISUI/ISPanel"

-- Events and globals: docs/api/Events.md, docs/api/globals.md.
-- World-following UI: Foraging/ISBaseIcon.lua:847-849; UIElement consumeMouseEvents
-- defaults true (ui/UIElement.java:64). isoToScreenX/Y already include zoom
-- (Lua/LuaManager.java:3515-3533). Java zombie lists are 0-indexed.
-- Right-click: UIElement.onRightMouseDown treats a Lua nil return as consumed
-- (ui/UIElement.java:1540-1550) and does not consult consumeMouseEvents. That
-- calls Mouse.UIBlockButtonDown(1) (ui/UIManager.java:747-754), which makes
-- GameKeyboard Aim (mouse button 1) fail isButtonDownUICheck
-- (input/Mouse.java:93-114). ISBaseIcon returns false (Foraging/ISBaseIcon.lua:45-46).
-- While RMB is held, any isOverElement hit also marks consumedRClick
-- (ui/UIManager.java:787-793). maxDrawHeight 0 fails that hit test
-- (ui/UIManager.java:494-499) without clipping Lua drawing.

local SHOW_INDICATORS = true
local DISPLAY_CAP = 12
local DISCOVER_RANGE_SQ = 20 * 20
local DISCOVER_MS = 200
local MAX_PLAYERS = 4
local RAY_COUNT = 16
local BAR_WIDTH = 28
local BAR_HEIGHT = 5
local ANCHOR_OFFSET_Y = -40
local DUMP_INTERVAL_MS = 1000
local DUMP_RANGE_SQUARED = 20 * 20

-- Prototype colors. Not a visual commitment.
local FILL_SUSPICIOUS = { r = 0.90, g = 0.75, b = 0.20, a = 0.90 }
local FILL_DETECTED = { r = 0.90, g = 0.18, b = 0.12, a = 0.95 }
local BAR_BACK = { r = 0.05, g = 0.05, b = 0.05, a = 0.70 }

local lastDumpMs = 0

StealthOverhaul = StealthOverhaul or {}

if StealthOverhaul.layers then
    for i = 0, MAX_PLAYERS - 1 do
        local existing = StealthOverhaul.layers[i]
        if existing ~= nil then
            existing:removeFromUIManager()
        end
    end
end
StealthOverhaul.layers = {}

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

-- AwarenessState.DETECTED is ordinal 2 (AwarenessState.java). Do not compare
-- the getter result to StealthOverhaulAPI.STATE_DETECTED with ==. Those are
-- different Java values; Kahlua == does not compare their numeric value, so
-- a chasing zombie (console state=2) was drawn as a suspicion fill and hidden
-- once decay reached 0.
local DETECTED_STATE = 2

---@param state number|nil
---@return boolean
local function isDetectedState(state)
    return tonumber(tostring(state)) == DETECTED_STATE
end

---@return StealthOverhaulSlot
local function newSlot()
    ---@class StealthOverhaulSlot
    ---@field zombie IsoZombie|nil
    ---@field seen boolean
    ---@field distSq number
    ---@field awareness number
    ---@field state integer
    ---@field wx number[]
    ---@field wy number[]
    ---@field innerWx number[]
    ---@field innerWy number[]
    ---@field originX number
    ---@field originY number
    ---@field originZ number
    ---@field lookX number
    ---@field lookY number
    ---@field range number
    ---@field revision integer
    ---@field lastRefreshMs number
    ---@field validCone boolean
    local slot = {
        zombie = nil,
        seen = false,
        distSq = 0,
        awareness = 0,
        state = 0,
        wx = {},
        wy = {},
        innerWx = {},
        innerWy = {},
        originX = 0,
        originY = 0,
        originZ = 0,
        lookX = 0,
        lookY = 0,
        range = 0,
        revision = -1,
        lastRefreshMs = 0,
        validCone = false,
    }
    for i = 1, RAY_COUNT do
        slot.wx[i] = 0
        slot.wy[i] = 0
        slot.innerWx[i] = 0
        slot.innerWy[i] = 0
    end
    return slot
end

---@param awareness number
---@param distSq number
---@param otherAwareness number
---@param otherDistSq number
---@return boolean
local function isHigherPriority(awareness, distSq, otherAwareness, otherDistSq)
    if awareness ~= otherAwareness then
        return awareness > otherAwareness
    end
    return distSq < otherDistSq
end

---@class StealthOverhaulLayer : ISPanel
---@field playerIndex integer
---@field slots StealthOverhaulSlot[]
---@field lastDiscoverMs number
---@field coneCursor integer
---@field patchActive boolean
local Layer = ISPanel:derive("StealthOverhaulLayer")
Layer.Type = "StealthOverhaulLayer"

function Layer:initialise()
    ISPanel.initialise(self)
end

function Layer:prerender()
end

---@param x number
---@param y number
---@return boolean
function Layer:onMouseDown(x, y)
    return false
end

---@param x number
---@param y number
---@return boolean
function Layer:onMouseUp(x, y)
    return false
end

---@param dx number
---@param dy number
---@return boolean
function Layer:onMouseMove(dx, dy)
    return false
end

---@param x number
---@param y number
---@return boolean
function Layer:onRightMouseDown(x, y)
    return false
end

---@param x number
---@param y number
---@return boolean
function Layer:onRightMouseUp(x, y)
    return false
end

---@param del number
---@return boolean
function Layer:onMouseWheel(del)
    return false
end

---@param player IsoPlayer
---@param zombie IsoZombie
---@return boolean
function Layer:isRenderableZombie(player, zombie)
    if zombie == nil or zombie:isDead() then
        return false
    end
    if player:DistToSquared(zombie) > DISCOVER_RANGE_SQ then
        return false
    end
    local square = zombie:getCurrentSquare()
    if square == nil then
        square = zombie:getSquare()
    end
    if square == nil then
        return false
    end
    if math.abs(zombie:getZ() - player:getZ()) > 0.5 then
        return false
    end
    if zombie:isAlphaZero(self.playerIndex) then
        return false
    end
    return true
end

---@param player IsoPlayer
function Layer:discover(player)
    local cell = getCell()
    if cell == nil then
        return
    end
    local zombies = cell:getZombieList()
    if zombies == nil then
        return
    end

    local slots = self.slots
    for i = 1, DISPLAY_CAP do
        slots[i].seen = false
    end

    local playerIndex = self.playerIndex
    for i = 0, zombies:size() - 1 do
        local zombie = zombies:get(i)
        if self:isRenderableZombie(player, zombie) then
            local distSq = player:DistToSquared(zombie)
            local awareness = StealthOverhaulAPI.getAwareness(zombie, player)
            local state = StealthOverhaulAPI.getAwarenessState(zombie, player)

            local existing = nil
            local empty = nil
            local worst = nil
            for s = 1, DISPLAY_CAP do
                local slot = slots[s]
                if slot.zombie == zombie then
                    existing = slot
                    break
                elseif slot.zombie == nil then
                    if empty == nil then
                        empty = slot
                    end
                elseif worst == nil or isHigherPriority(worst.awareness, worst.distSq, slot.awareness, slot.distSq) then
                    worst = slot
                end
            end

            if existing ~= nil then
                existing.seen = true
                existing.distSq = distSq
                existing.awareness = awareness
                existing.state = state
            elseif empty ~= nil then
                empty.zombie = zombie
                empty.seen = true
                empty.distSq = distSq
                empty.awareness = awareness
                empty.state = state
                empty.validCone = false
            elseif worst ~= nil and isHigherPriority(awareness, distSq, worst.awareness, worst.distSq) then
                worst.zombie = zombie
                worst.seen = true
                worst.distSq = distSq
                worst.awareness = awareness
                worst.state = state
                worst.validCone = false
            end
        end
    end

    for i = 1, DISPLAY_CAP do
        local slot = slots[i]
        if not slot.seen then
            slot.zombie = nil
            slot.validCone = false
            slot.awareness = 0
            slot.state = 0
        end
    end
end

function Layer:clearSlots()
    for i = 1, DISPLAY_CAP do
        local slot = self.slots[i]
        slot.zombie = nil
        slot.seen = false
        slot.validCone = false
        slot.awareness = 0
        slot.state = 0
    end
end

function Layer:syncViewport()
    local playerIndex = self.playerIndex
    local width = getPlayerScreenWidth(playerIndex)
    local height = getPlayerScreenHeight(playerIndex)
    if width < 1 then
        width = 1
    end
    if height < 1 then
        height = 1
    end
    -- World-following UI is positioned in viewport space (ISBaseIcon.lua:390-412).
    self:setX(0)
    self:setY(0)
    self:setWidth(width)
    self:setHeight(height)
end

function Layer:update()
    self.patchActive = isPatchActive()
    if not self.patchActive then
        self:setVisible(false)
        self:clearSlots()
        return
    end

    local player = getSpecificPlayer(self.playerIndex)
    if player == nil or player:isDead() then
        self:setVisible(false)
        self:clearSlots()
        return
    end

    self:setVisible(true)
    self:syncViewport()

    local now = getTimestampMs()
    if now - self.lastDiscoverMs >= DISCOVER_MS then
        self.lastDiscoverMs = now
        self:discover(player)
    end

    if StealthOverhaul.refreshCones ~= nil then
        StealthOverhaul.refreshCones(self, player, now)
    end
end

---@param slot StealthOverhaulSlot
---@param player IsoPlayer
---@return boolean
function Layer:revalidateSlot(slot, player)
    local zombie = slot.zombie
    if zombie == nil then
        return false
    end
    if not self:isRenderableZombie(player, zombie) then
        slot.zombie = nil
        slot.validCone = false
        return false
    end
    slot.awareness = StealthOverhaulAPI.getAwareness(zombie, player)
    slot.state = StealthOverhaulAPI.getAwarenessState(zombie, player)
    return true
end

---@param x number
---@param y number
---@param fill number
---@param detected boolean
function Layer:drawAwarenessBar(x, y, fill, detected)
    local left = x - BAR_WIDTH * 0.5
    local top = y + ANCHOR_OFFSET_Y
    if left + BAR_WIDTH < 0 or left > self.width or top + BAR_HEIGHT < 0 or top > self.height then
        return
    end
    self:drawRect(left, top, BAR_WIDTH, BAR_HEIGHT, BAR_BACK.a, BAR_BACK.r, BAR_BACK.g, BAR_BACK.b)
    local color = detected and FILL_DETECTED or FILL_SUSPICIOUS
    if fill < 0 then
        fill = 0
    elseif fill > 1 then
        fill = 1
    end
    local filled = math.floor(BAR_WIDTH * fill)
    if fill > 0 and filled < 1 then
        filled = 1
    end
    if filled > 0 then
        self:drawRect(left, top, filled, BAR_HEIGHT, color.a, color.r, color.g, color.b)
    end
    self:drawRectBorder(left, top, BAR_WIDTH, BAR_HEIGHT, color.a, color.r, color.g, color.b)
end

function Layer:render()
    if not self:getIsVisible() or not self.patchActive then
        return
    end
    local player = getSpecificPlayer(self.playerIndex)
    if player == nil or player:isDead() then
        return
    end

    local playerIndex = self.playerIndex
    local dx = -getPlayerScreenLeft(playerIndex)
    local dy = -getPlayerScreenTop(playerIndex)
    local threshold = StealthOverhaulAPI.getDetectionThreshold()

    for i = 1, DISPLAY_CAP do
        self:revalidateSlot(self.slots[i], player)
    end

    if StealthOverhaul.drawCones ~= nil then
        StealthOverhaul.drawCones(self, player, dx, dy)
    end

    if not SHOW_INDICATORS then
        return
    end

    for i = 1, DISPLAY_CAP do
        local slot = self.slots[i]
        local zombie = slot.zombie
        if zombie ~= nil then
            local showDetected = isDetectedState(slot.state)
            if slot.awareness > 0 or showDetected then
                local sx = isoToScreenX(playerIndex, zombie:getX(), zombie:getY(), zombie:getZ()) + dx
                local sy = isoToScreenY(playerIndex, zombie:getX(), zombie:getY(), zombie:getZ()) + dy
                local fill = 0
                if threshold > 0 then
                    fill = slot.awareness / threshold
                end
                if showDetected and fill < 1 then
                    fill = 1
                end
                self:drawAwarenessBar(sx, sy, fill, showDetected)
            end
        end
    end
end

---@param playerIndex integer
---@return StealthOverhaulLayer
function Layer:new(playerIndex)
    local o = ISPanel:new(0, 0, 1, 1)
    setmetatable(o, self)
    self.__index = self
    o.playerIndex = playerIndex
    o.keepOnScreen = false
    o.background = false
    o.moveWithMouse = false
    o.wantMouseEvents = false
    o.wantKeyEvents = false
    o.lastDiscoverMs = 0
    o.coneCursor = 1
    o.patchActive = false
    o.slots = {}
    for i = 1, DISPLAY_CAP do
        o.slots[i] = newSlot()
    end
    return o
end

---@param playerIndex integer
local function ensureLayer(playerIndex)
    if playerIndex < 0 or playerIndex >= MAX_PLAYERS then
        return
    end
    local layer = StealthOverhaul.layers[playerIndex]
    if layer == nil then
        layer = Layer:new(playerIndex)
        layer:initialise()
        layer:instantiate()
        layer:setFollowGameWorld(true)
        layer:setRenderThisPlayerOnly(playerIndex)
        layer:setWantMouseEvents(false)
        layer:setWantKeyEvents(false)
        layer:setMaxDrawHeight(0)
        layer:noBackground()
        layer:addToUIManager()
        StealthOverhaul.layers[playerIndex] = layer
    end
    layer:setVisible(true)
end

local function hideAllLayers()
    for i = 0, MAX_PLAYERS - 1 do
        local layer = StealthOverhaul.layers[i]
        if layer ~= nil then
            layer:setVisible(false)
            layer:clearSlots()
        end
    end
end

---@param zombie IsoZombie
---@param player IsoPlayer
local function dumpPair(zombie, player)
    local ok, line = pcall(function()
        local awareness = StealthOverhaulAPI.getAwareness(zombie, player)
        local exposed = StealthOverhaulAPI.wasLastExposed(zombie, player)
        if awareness <= 0 and not exposed then
            return nil
        end
        return " awareness="
            .. tostring(awareness)
            .. " state="
            .. tostring(StealthOverhaulAPI.getAwarenessState(zombie, player))
            .. " dist="
            .. tostring(StealthOverhaulAPI.getLastDistance(zombie, player))
            .. " facing="
            .. tostring(StealthOverhaulAPI.getLastFacingDot(zombie, player))
            .. " gain="
            .. tostring(StealthOverhaulAPI.getLastGainMultiplier(zombie, player))
            .. " range="
            .. tostring(StealthOverhaulAPI.getEffectiveRange(zombie, player))
            .. " exposed="
            .. tostring(exposed)
            .. " reason="
            .. tostring(StealthOverhaulAPI.getLastBlockedReason(zombie, player))
    end)
    if ok and line ~= nil then
        print(
            "[StealthOverhaul] pair player="
                .. tostring(player:getPlayerNum())
                .. line
        )
    end
end

---@param tick number
local function onTick(tick)
    if not getDebug() or not isPatchActive() then
        return
    end
    local now = getTimestampMs()
    if now - lastDumpMs < DUMP_INTERVAL_MS then
        return
    end
    lastDumpMs = now

    local cell = getCell()
    if cell == nil then
        return
    end
    local zombies = cell:getZombieList()
    if zombies == nil then
        return
    end

    local playerCount = getNumActivePlayers()
    for playerIndex = 0, playerCount - 1 do
        local player = getSpecificPlayer(playerIndex)
        if player ~= nil and not player:isDead() then
            for i = 0, zombies:size() - 1 do
                local zombie = zombies:get(i)
                if zombie ~= nil and not zombie:isDead() and player:DistToSquared(zombie) <= DUMP_RANGE_SQUARED then
                    dumpPair(zombie, player)
                end
            end
        end
    end
end

local function onGameStart()
    if not isPatchActive() then
        hideAllLayers()
        return
    end
    local playerCount = getNumActivePlayers()
    for playerIndex = 0, playerCount - 1 do
        ensureLayer(playerIndex)
    end
end

---@param playerIndex integer
---@param player IsoPlayer
local function onCreatePlayer(playerIndex, player)
    if playerIndex == nil then
        return
    end
    if isPatchActive() then
        ensureLayer(playerIndex)
    end
end

---@param player IsoPlayer
local function onPlayerDeath(player)
    if player == nil then
        return
    end
    local playerIndex = player:getPlayerNum()
    local layer = StealthOverhaul.layers[playerIndex]
    if layer ~= nil then
        layer:setVisible(false)
        layer:clearSlots()
    end
end

if StealthOverhaul.onTick then
    Events.OnTick.Remove(StealthOverhaul.onTick)
end
if StealthOverhaul.onGameStartIndicators then
    Events.OnGameStart.Remove(StealthOverhaul.onGameStartIndicators)
end
if StealthOverhaul.onCreatePlayer then
    Events.OnCreatePlayer.Remove(StealthOverhaul.onCreatePlayer)
end
if StealthOverhaul.onPlayerDeath then
    Events.OnPlayerDeath.Remove(StealthOverhaul.onPlayerDeath)
end

StealthOverhaul.onTick = onTick
StealthOverhaul.onGameStartIndicators = onGameStart
StealthOverhaul.onCreatePlayer = onCreatePlayer
StealthOverhaul.onPlayerDeath = onPlayerDeath
StealthOverhaul.displayCap = DISPLAY_CAP
StealthOverhaul.rayCount = RAY_COUNT

Events.OnTick.Add(onTick)
Events.OnGameStart.Add(onGameStart)
Events.OnCreatePlayer.Add(onCreatePlayer)
Events.OnPlayerDeath.Add(onPlayerDeath)
