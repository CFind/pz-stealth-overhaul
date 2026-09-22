--[[
    Stealth Overhaul
    Client display options. Values are read while the world is running, so
    accepting them in the MODS page applies without a restart.

    PZAPI.ModOptions:create (client/PZAPI/ModOptions.lua:247). Tick boxes,
    sliders, and color pickers are addTickBox, addSlider, and addColorPicker
    (ModOptions.lua:64, :206, :158). Names and tooltips are translation keys;
    the options page passes them through getText (MainOptions.lua:2806,
    :2817, :2946). A missing key is logged and shown as the key itself
    (core/Translator.java:496). Saved values are read from ModOptions.ini by
    PZAPI.ModOptions:load (ModOptions.lua:292), which the options page calls
    when it opens (MainOptions.lua:2796). OnGameBoot runs after client Lua
    has loaded (GameWindow.java:674), so loading there applies the last
    accepted values before play. Accept writes each control into option.value
    (MainOptions.lua:2829-3040) before Options:apply. IGUI_ strings live in
    Translate/EN/IG_UI.json (core/Translator.java:136, :354, :434). Picking a
    color forces alpha to 1 (MainOptions.lua:3247), so each color has an
    opacity slider.

    The slider branch does not copy option.tooltip onto the control
    (MainOptions.lua:3024-3048). ISLabel shows self.tooltip while the pointer
    is over the label (ISUI/ISLabel.lua:110-130), so after the page is built
    the slider name label gets that tooltip. Tick boxes and color buttons
    already receive theirs (MainOptions.lua:2816-2818, :2945-2947).
    There is no hide flag. setEnabled only disables a control
    (ModOptions.lua:74-78). The page is one pass of widgets
    (MainOptions.lua:2805-3053). A tick box onChange runs on click
    (MainOptions.lua:2836-2838), before Accept stores the value
    (MainOptions.lua:2834). Hidden rows are setVisible(false)
    (ISUIElement.lua:657) and the rows below are setY'd up
    (ISUIElement.lua:989); the scroll height follows
    (ISUIElement.lua:1627).

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

local OPTIONS_ID = "StealthOverhaul"

local SUSPICIOUS_COLOR = { r = 0.90, g = 0.75, b = 0.20, a = 0.90 }
local DETECTED_COLOR = { r = 0.90, g = 0.18, b = 0.12, a = 0.95 }
local BAR_BACK_COLOR = { r = 0.05, g = 0.05, b = 0.05, a = 0.70 }
local CONE_INNER_COLOR = { r = 0.95, g = 0.85, b = 0.20, a = 0.22 }
local CONE_OUTER_COLOR = { r = 0.95, g = 0.55, b = 0.10, a = 0.10 }
local CONE_LINE_COLOR = { r = 0.95, g = 0.80, b = 0.25, a = 0.55 }

StealthOverhaul = StealthOverhaul or {}

---@return PZAPI.ModOptions.Options|nil
local function section()
    if PZAPI == nil or PZAPI.ModOptions == nil then
        return nil
    end
    return PZAPI.ModOptions:getOptions(OPTIONS_ID)
end

---@param id string
---@return unknown
local function optionValue(id)
    local options = section()
    if options == nil then
        return nil
    end
    local option = options:getOption(id)
    if option == nil or option.getValue == nil then
        return nil
    end
    return option:getValue()
end

---@param id string
---@param fallback number
---@param minValue number
---@param maxValue number
---@return number
local function clampedNumber(id, fallback, minValue, maxValue)
    local value = tonumber(optionValue(id))
    if value == nil then
        value = fallback
    end
    if value < minValue then
        return minValue
    end
    if value > maxValue then
        return maxValue
    end
    return value
end

---@param id string
---@param fallback integer
---@param minValue integer
---@param maxValue integer
---@return integer
local function clampedInt(id, fallback, minValue, maxValue)
    return math.floor(clampedNumber(id, fallback, minValue, maxValue) + 0.5)
end

---@param id string
---@param fallback boolean
---@return boolean
local function boolOption(id, fallback)
    local value = optionValue(id)
    if value == nil then
        return fallback
    end
    return value == true
end

local colorScratch = {}

---@param id string
---@param alphaId string
---@param fallback umbrella.RGBA
---@return umbrella.RGBA
local function colorOption(id, alphaId, fallback)
    local scratch = colorScratch[id]
    if scratch == nil then
        scratch = { r = fallback.r, g = fallback.g, b = fallback.b, a = fallback.a }
        colorScratch[id] = scratch
    end
    local value = optionValue(id)
    if type(value) == "table" and value.r ~= nil and value.g ~= nil and value.b ~= nil then
        scratch.r = value.r
        scratch.g = value.g
        scratch.b = value.b
    else
        scratch.r = fallback.r
        scratch.g = fallback.g
        scratch.b = fallback.b
    end
    scratch.a = clampedNumber(alphaId, fallback.a, 0, 1)
    return scratch
end

---@return boolean
function StealthOverhaul.showIndicators()
    return boolOption("showIndicators", true)
end

---@return integer
function StealthOverhaul.displayCap()
    return clampedInt("displayCap", 12, 1, 24)
end

---@return number
function StealthOverhaul.discoverRangeSq()
    local range = clampedInt("discoverRange", 20, 4, 60)
    return range * range
end

---@return integer
function StealthOverhaul.discoverMs()
    return clampedInt("discoverMs", 200, 50, 2000)
end

---@return integer
function StealthOverhaul.rayCount()
    return clampedInt("rayCount", 30, 4, 48)
end

---@return integer
function StealthOverhaul.barWidth()
    return clampedInt("barWidth", 28, 8, 96)
end

---@return integer
function StealthOverhaul.barHeight()
    return clampedInt("barHeight", 5, 1, 32)
end

---@return integer
function StealthOverhaul.anchorOffsetY()
    return clampedInt("anchorOffsetY", -40, -120, 40)
end

---@return integer
function StealthOverhaul.dumpIntervalMs()
    return clampedInt("dumpIntervalMs", 1000, 250, 10000)
end

---@return number
function StealthOverhaul.dumpRangeSq()
    local range = clampedInt("dumpRange", 20, 5, 60)
    return range * range
end

---@return umbrella.RGBA
function StealthOverhaul.suspiciousColor()
    return colorOption("suspiciousColor", "suspiciousAlpha", SUSPICIOUS_COLOR)
end

---@return umbrella.RGBA
function StealthOverhaul.detectedColor()
    return colorOption("detectedColor", "detectedAlpha", DETECTED_COLOR)
end

---@return umbrella.RGBA
function StealthOverhaul.barBackColor()
    return colorOption("barBackColor", "barBackAlpha", BAR_BACK_COLOR)
end

---@return boolean
function StealthOverhaul.showCones()
    return boolOption("showCones", true)
end

---@return number
function StealthOverhaul.innerBand()
    return clampedNumber("innerBand", 0.45, 0.1, 0.9)
end

---@return integer
function StealthOverhaul.coneMaxAgeMs()
    return clampedInt("coneMaxAgeMs", 400, 50, 2000)
end

---@return integer
function StealthOverhaul.coneRefreshPerUpdate()
    return clampedInt("coneRefreshPerUpdate", 8, 1, 12)
end

---@return integer
function StealthOverhaul.losSteps()
    return clampedInt("losSteps", 8, 1, 16)
end

---@return number
function StealthOverhaul.moveThresholdSq()
    local distance = clampedNumber("moveDistance", 0.05, 0.05, 2.0)
    return distance * distance
end

---@return number
function StealthOverhaul.lookDotMin()
    return clampedNumber("lookDotMin", 0.995, 0.9, 1.0)
end

---@return number
function StealthOverhaul.sampleDistance()
    return clampedNumber("sampleDistance", 2.0, 1.0, 8.0)
end

---@return integer
function StealthOverhaul.halfAngleIters()
    return clampedInt("halfAngleIters", 16, 4, 32)
end

---@return umbrella.RGBA
function StealthOverhaul.coneInnerColor()
    return colorOption("coneInnerColor", "coneInnerAlpha", CONE_INNER_COLOR)
end

---@return umbrella.RGBA
function StealthOverhaul.coneOuterColor()
    return colorOption("coneOuterColor", "coneOuterAlpha", CONE_OUTER_COLOR)
end

---@return umbrella.RGBA
function StealthOverhaul.coneLineColor()
    return colorOption("coneLineColor", "coneLineAlpha", CONE_LINE_COLOR)
end

---@param screen MainOptions
local function attachSliderTooltips(screen)
    local options = section()
    if options == nil or screen.mainPanel == nil then
        return
    end
    local byName = {}
    for i = 1, #options.data do
        local option = options.data[i]
        if option.type == "slider" and option.tooltip ~= nil then
            local tip = getText(option.tooltip)
            byName[getText(option.name)] = tip
            local element = option.element
            if element ~= nil and element.label ~= nil and element.label.setTooltip ~= nil then
                element.label:setTooltip(tip)
            end
        end
    end
    local children = screen.mainPanel:getChildrenInOrder()
    for i = 1, #children do
        local child = children[i]
        local tip = child.name ~= nil and byName[child.name] or nil
        if tip ~= nil and child.setTooltip ~= nil then
            child:setTooltip(tip)
        end
    end
end

---@type { panel: ISUIElement, rows: table, foreign: table, sectionEnd: number, scrollHeight: number }|nil
local optionsLayout = nil

---@param widget ISUIElement
---@return string|nil
local function widgetLabel(widget)
    if widget.getName == nil then
        return nil
    end
    return widget:getName()
end

---@param panel ISUIElement
---@param option umbrella.ModOptions.Element
---@param claimed table<ISUIElement, boolean>
---@return ISUIElement[]|nil
local function rowWidgets(panel, option, claimed)
    local children = panel:getChildrenInOrder()
    if option.type == "description" then
        for i = 1, #children do
            local child = children[i]
            if not claimed[child] and child.Type == "ISRichTextPanel" and child.text == option.text then
                claimed[child] = true
                return { child }
            end
        end
        return nil
    end
    if option.type == "title" then
        local wanted = getText(option.name)
        for i = 1, #children do
            local child = children[i]
            if not claimed[child] and widgetLabel(child) == wanted then
                claimed[child] = true
                return { child }
            end
        end
        return nil
    end
    if option.element == nil then
        return nil
    end
    local rowRoot = option.element
    if rowRoot.parent ~= nil and rowRoot.parent ~= panel then
        rowRoot = rowRoot.parent
    end
    local label = nil
    local previous = nil
    for i = 1, #children do
        local child = children[i]
        if child == rowRoot then
            label = previous
            break
        end
        if child ~= panel.vscroll and child ~= panel.hscroll then
            previous = child
        end
    end
    local widgets = {}
    if label ~= nil and not claimed[label] then
        claimed[label] = true
        widgets[#widgets + 1] = label
    end
    claimed[rowRoot] = true
    widgets[#widgets + 1] = rowRoot
    return widgets
end

---@param screen MainOptions
local function captureAdvancedLayout(screen)
    local options = section()
    local panel = screen.mainPanel
    if options == nil or panel == nil then
        optionsLayout = nil
        return
    end
    local claimed = {}
    local rows = {}
    for i = 1, #options.data do
        local option = options.data[i]
        local widgets = rowWidgets(panel, option, claimed)
        if widgets == nil or #widgets == 0 then
            print("[StealthOverhaul] options row not found: " .. tostring(option.id or option.name or option.type))
        else
            local homeY = widgets[1]:getY()
            for w = 2, #widgets do
                local y = widgets[w]:getY()
                if y < homeY then
                    homeY = y
                end
            end
            local offsets = {}
            for w = 1, #widgets do
                offsets[w] = widgets[w]:getY() - homeY
            end
            rows[#rows + 1] = {
                basic = option.basic == true,
                homeY = homeY,
                height = 0,
                widgets = widgets,
                offsets = offsets,
            }
        end
    end
    if #rows == 0 then
        optionsLayout = nil
        return
    end
    for i = 1, #rows - 1 do
        rows[i].height = rows[i + 1].homeY - rows[i].homeY
        if rows[i].height < 1 then
            rows[i].height = 1
        end
    end
    local last = rows[#rows]
    local children = panel:getChildrenInOrder()
    local nextY = nil
    for i = 1, #children do
        local child = children[i]
        if not claimed[child] and child ~= panel.vscroll and child ~= panel.hscroll then
            local y = child:getY()
            if y > last.homeY + 0.5 and (nextY == nil or y < nextY) then
                nextY = y
            end
        end
    end
    local scrollHeight = panel:getScrollHeight()
    if nextY ~= nil then
        last.height = nextY - last.homeY
    else
        last.height = math.max(scrollHeight - last.homeY, 1)
        nextY = last.homeY + last.height
    end
    local foreign = {}
    for i = 1, #children do
        local child = children[i]
        if not claimed[child]
            and child ~= panel.vscroll
            and child ~= panel.hscroll
            and child:getY() >= nextY - 0.5
        then
            foreign[#foreign + 1] = { widget = child, homeY = child:getY() }
        end
    end
    optionsLayout = {
        panel = panel,
        rows = rows,
        foreign = foreign,
        sectionEnd = nextY,
        scrollHeight = scrollHeight,
    }
end

---@param advanced boolean
local function applyAdvancedLayout(advanced)
    local layout = optionsLayout
    if layout == nil or #layout.rows == 0 then
        return
    end
    local cursor = layout.rows[1].homeY
    for i = 1, #layout.rows do
        local row = layout.rows[i]
        local show = row.basic or advanced
        for w = 1, #row.widgets do
            local widget = row.widgets[w]
            if show then
                widget:setVisible(true)
                widget:setY(cursor + row.offsets[w])
            else
                widget:setY(-10000)
                widget:setVisible(false)
            end
        end
        if show then
            cursor = cursor + row.height
        end
    end
    local delta = cursor - layout.sectionEnd
    for i = 1, #layout.foreign do
        local item = layout.foreign[i]
        item.widget:setY(item.homeY + delta)
    end
    layout.panel:setScrollHeight(layout.scrollHeight + delta)
end

---@return boolean
local function showAdvancedRows()
    local options = section()
    if options == nil then
        return false
    end
    local option = options:getOption("advanced")
    if option == nil or option.getValue == nil then
        return false
    end
    return option:getValue() == true
end

if PZAPI ~= nil and PZAPI.ModOptions ~= nil and PZAPI.ModOptions:getOptions(OPTIONS_ID) == nil then
    local options = PZAPI.ModOptions:create(OPTIONS_ID, "IGUI_StealthOverhaul_Options")
    options:addDescription("IGUI_StealthOverhaul_Description")
    local advanced = options:addTickBox(
        "advanced",
        "IGUI_StealthOverhaul_Advanced",
        false,
        "IGUI_StealthOverhaul_Advanced_tt"
    )
    advanced.onChange = function(_, selected)
        applyAdvancedLayout(selected == true)
    end

    options:addTitle("IGUI_StealthOverhaul_IndicatorsTitle")
    options:addTickBox(
        "showIndicators",
        "IGUI_StealthOverhaul_ShowIndicators",
        true,
        "IGUI_StealthOverhaul_ShowIndicators_tt"
    )
    options:addSlider(
        "displayCap",
        "IGUI_StealthOverhaul_DisplayCap",
        1,
        24,
        1,
        12,
        "IGUI_StealthOverhaul_DisplayCap_tt"
    )
    options:addSlider(
        "discoverRange",
        "IGUI_StealthOverhaul_DiscoverRange",
        4,
        60,
        1,
        20,
        "IGUI_StealthOverhaul_DiscoverRange_tt"
    )
    options:addSlider(
        "discoverMs",
        "IGUI_StealthOverhaul_DiscoverMs",
        50,
        2000,
        50,
        200,
        "IGUI_StealthOverhaul_DiscoverMs_tt"
    )
    options:addSlider(
        "rayCount",
        "IGUI_StealthOverhaul_RayCount",
        4,
        48,
        1,
        16,
        "IGUI_StealthOverhaul_RayCount_tt"
    )
    options:addSlider(
        "barWidth",
        "IGUI_StealthOverhaul_BarWidth",
        8,
        96,
        1,
        28,
        "IGUI_StealthOverhaul_BarWidth_tt"
    )
    options:addSlider(
        "barHeight",
        "IGUI_StealthOverhaul_BarHeight",
        1,
        32,
        1,
        5,
        "IGUI_StealthOverhaul_BarHeight_tt"
    )
    options:addSlider(
        "anchorOffsetY",
        "IGUI_StealthOverhaul_AnchorOffsetY",
        -120,
        40,
        1,
        -40,
        "IGUI_StealthOverhaul_AnchorOffsetY_tt"
    )

    options:addTitle("IGUI_StealthOverhaul_ColorsTitle")
    options:addColorPicker(
        "suspiciousColor",
        "IGUI_StealthOverhaul_SuspiciousColor",
        SUSPICIOUS_COLOR.r,
        SUSPICIOUS_COLOR.g,
        SUSPICIOUS_COLOR.b,
        SUSPICIOUS_COLOR.a,
        "IGUI_StealthOverhaul_SuspiciousColor_tt"
    )
    options:addSlider(
        "suspiciousAlpha",
        "IGUI_StealthOverhaul_SuspiciousAlpha",
        0,
        1,
        0.01,
        SUSPICIOUS_COLOR.a,
        "IGUI_StealthOverhaul_SuspiciousAlpha_tt"
    )
    options:addColorPicker(
        "detectedColor",
        "IGUI_StealthOverhaul_DetectedColor",
        DETECTED_COLOR.r,
        DETECTED_COLOR.g,
        DETECTED_COLOR.b,
        DETECTED_COLOR.a,
        "IGUI_StealthOverhaul_DetectedColor_tt"
    )
    options:addSlider(
        "detectedAlpha",
        "IGUI_StealthOverhaul_DetectedAlpha",
        0,
        1,
        0.01,
        DETECTED_COLOR.a,
        "IGUI_StealthOverhaul_DetectedAlpha_tt"
    )
    options:addColorPicker(
        "barBackColor",
        "IGUI_StealthOverhaul_BarBackColor",
        BAR_BACK_COLOR.r,
        BAR_BACK_COLOR.g,
        BAR_BACK_COLOR.b,
        BAR_BACK_COLOR.a,
        "IGUI_StealthOverhaul_BarBackColor_tt"
    )
    options:addSlider(
        "barBackAlpha",
        "IGUI_StealthOverhaul_BarBackAlpha",
        0,
        1,
        0.01,
        BAR_BACK_COLOR.a,
        "IGUI_StealthOverhaul_BarBackAlpha_tt"
    )

    options:addTitle("IGUI_StealthOverhaul_ConesTitle")
    options:addTickBox(
        "showCones",
        "IGUI_StealthOverhaul_ShowCones",
        true,
        "IGUI_StealthOverhaul_ShowCones_tt"
    )
    options:addSlider(
        "innerBand",
        "IGUI_StealthOverhaul_InnerBand",
        0.1,
        0.9,
        0.05,
        0.45,
        "IGUI_StealthOverhaul_InnerBand_tt"
    )
    options:addSlider(
        "coneMaxAgeMs",
        "IGUI_StealthOverhaul_ConeMaxAge",
        50,
        2000,
        50,
        400,
        "IGUI_StealthOverhaul_ConeMaxAge_tt"
    )
    options:addSlider(
        "coneRefreshPerUpdate",
        "IGUI_StealthOverhaul_ConeRefresh",
        1,
        12,
        1,
        2,
        "IGUI_StealthOverhaul_ConeRefresh_tt"
    )
    options:addSlider(
        "losSteps",
        "IGUI_StealthOverhaul_LosSteps",
        1,
        16,
        1,
        8,
        "IGUI_StealthOverhaul_LosSteps_tt"
    )
    options:addSlider(
        "moveDistance",
        "IGUI_StealthOverhaul_MoveDistance",
        0.05,
        2.0,
        0.05,
        0.15,
        "IGUI_StealthOverhaul_MoveDistance_tt"
    )
    options:addSlider(
        "lookDotMin",
        "IGUI_StealthOverhaul_LookDot",
        0.9,
        1.0,
        0.001,
        0.995,
        "IGUI_StealthOverhaul_LookDot_tt"
    )
    options:addSlider(
        "sampleDistance",
        "IGUI_StealthOverhaul_SampleDistance",
        1.0,
        8.0,
        0.5,
        2.0,
        "IGUI_StealthOverhaul_SampleDistance_tt"
    )
    options:addSlider(
        "halfAngleIters",
        "IGUI_StealthOverhaul_HalfAngleIters",
        4,
        32,
        1,
        16,
        "IGUI_StealthOverhaul_HalfAngleIters_tt"
    )

    options:addTitle("IGUI_StealthOverhaul_ConeColorsTitle")
    options:addColorPicker(
        "coneInnerColor",
        "IGUI_StealthOverhaul_ConeInner",
        CONE_INNER_COLOR.r,
        CONE_INNER_COLOR.g,
        CONE_INNER_COLOR.b,
        CONE_INNER_COLOR.a,
        "IGUI_StealthOverhaul_ConeInner_tt"
    )
    options:addSlider(
        "coneInnerAlpha",
        "IGUI_StealthOverhaul_ConeInnerAlpha",
        0,
        1,
        0.01,
        CONE_INNER_COLOR.a,
        "IGUI_StealthOverhaul_ConeInnerAlpha_tt"
    )
    options:addColorPicker(
        "coneOuterColor",
        "IGUI_StealthOverhaul_ConeOuter",
        CONE_OUTER_COLOR.r,
        CONE_OUTER_COLOR.g,
        CONE_OUTER_COLOR.b,
        CONE_OUTER_COLOR.a,
        "IGUI_StealthOverhaul_ConeOuter_tt"
    )
    options:addSlider(
        "coneOuterAlpha",
        "IGUI_StealthOverhaul_ConeOuterAlpha",
        0,
        1,
        0.01,
        CONE_OUTER_COLOR.a,
        "IGUI_StealthOverhaul_ConeOuterAlpha_tt"
    )
    options:addColorPicker(
        "coneLineColor",
        "IGUI_StealthOverhaul_ConeLine",
        CONE_LINE_COLOR.r,
        CONE_LINE_COLOR.g,
        CONE_LINE_COLOR.b,
        CONE_LINE_COLOR.a,
        "IGUI_StealthOverhaul_ConeLine_tt"
    )
    options:addSlider(
        "coneLineAlpha",
        "IGUI_StealthOverhaul_ConeLineAlpha",
        0,
        1,
        0.01,
        CONE_LINE_COLOR.a,
        "IGUI_StealthOverhaul_ConeLineAlpha_tt"
    )

    options:addTitle("IGUI_StealthOverhaul_DebugTitle")
    options:addSlider(
        "dumpIntervalMs",
        "IGUI_StealthOverhaul_DumpInterval",
        250,
        10000,
        250,
        1000,
        "IGUI_StealthOverhaul_DumpInterval_tt"
    )
    options:addSlider(
        "dumpRange",
        "IGUI_StealthOverhaul_DumpRange",
        5,
        60,
        1,
        20,
        "IGUI_StealthOverhaul_DumpRange_tt"
    )

    local basicIds = {
        advanced = true,
        suspiciousColor = true,
        suspiciousAlpha = true,
        detectedColor = true,
        detectedAlpha = true,
        barBackColor = true,
        barBackAlpha = true,
        coneInnerColor = true,
        coneInnerAlpha = true,
        coneOuterColor = true,
        coneOuterAlpha = true,
        coneLineColor = true,
        coneLineAlpha = true,
    }
    local basicTitles = {
        IGUI_StealthOverhaul_ColorsTitle = true,
        IGUI_StealthOverhaul_ConeColorsTitle = true,
    }
    for i = 1, #options.data do
        local option = options.data[i]
        if option.type == "description" then
            option.basic = true
        elseif option.type == "title" and basicTitles[option.name] then
            option.basic = true
        elseif option.id ~= nil and basicIds[option.id] then
            option.basic = true
        end
    end
end

if MainOptions ~= nil
    and MainOptions.addModOptionsPanel ~= nil
    and MainOptions.addModOptionsPanel ~= StealthOverhaul.chainedAddModOptionsPanel
then
    StealthOverhaul.vanillaAddModOptionsPanel = MainOptions.addModOptionsPanel

    local function chainedAddModOptionsPanel(self)
        StealthOverhaul.vanillaAddModOptionsPanel(self)
        attachSliderTooltips(self)
        captureAdvancedLayout(self)
        applyAdvancedLayout(showAdvancedRows())
    end

    StealthOverhaul.chainedAddModOptionsPanel = chainedAddModOptionsPanel
    MainOptions.addModOptionsPanel = chainedAddModOptionsPanel
end

local function onGameBoot()
    if PZAPI ~= nil and PZAPI.ModOptions ~= nil then
        PZAPI.ModOptions:load()
    end
end

if StealthOverhaul.onGameBootOptions then
    Events.OnGameBoot.Remove(StealthOverhaul.onGameBootOptions)
end
StealthOverhaul.onGameBootOptions = onGameBoot
Events.OnGameBoot.Add(onGameBoot)
