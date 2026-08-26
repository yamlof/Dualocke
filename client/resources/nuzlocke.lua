local Game = {
	new = function (self, game)
		self.__index = self
		setmetatable(game, self)
		return game
	end
}

function Game.getParty(game)
	local party = {}
	local monStart = game._party
	local nameStart = game._partyNames
	local otStart = game._partyOt
	for i = 1, emu:read8(game._partyCount) do
		party[i] = game:_readPartyMon(monStart, nameStart, otStart)
		monStart = monStart + game._partyMonSize
		if game._partyNames then
			nameStart = nameStart + game._monNameLength + 1
		end
		if game._partyOt then
			otStart = otStart + game._playerNameLength + 1
		end
	end
	return party
end

function Game.toString(game, rawstring)
	local string = ""
	for _, char in ipairs({rawstring:byte(1, #rawstring)}) do
		if char == game._terminator then
			break
		end
		string = string..game._charmap[char]
	end
	return string
end

function Game.getSpeciesName(game, id)
	if game._speciesIndex then
		local index = game._index
		if not index then
			index = {}
			for i = 0, 255 do
				index[emu.memory.cart0:read8(game._speciesIndex + i)] = i
			end
			game._index = index
		end
		id = index[id]
	end
	local pointer = game._speciesNameTable + (game._speciesNameLength) * id
	return game:toString(emu.memory.cart0:readRange(pointer, game._monNameLength))
end

local function FlagGet(flagId)
    local saveBlock1 = emu:read32(0x03005008)
    if saveBlock1 == 0 then return false end
    local byte = emu:read8(saveBlock1 + 0xEE0 + (flagId >> 3))
    return ((byte >> (flagId & 7)) & 1) == 1
end

function Game.getBadges(game)
    if not game._badgeAddress then return 0 end

    if game._badgeAddress == true then
        local count = 0
        for flagId = 0x820, 0x827 do
            if FlagGet(flagId) then
                count = count + 1
            end
        end
        return count
    end

    return emu:read8(game._badgeAddress)
end

local MAP_NAMES = {
    -- Bank 1 (Dungeons)
    [0x0001] = "Viridian Forest",
    [0x0101] = "Mt Moon 1F",
    [0x0201] = "Mt Moon B1F",
    [0x0301] = "Mt Moon B2F",
    [0x2701] = "Rock Tunnel 1F",
    [0x2801] = "Rock Tunnel B1F",
    [0x2901] = "Seafoam Islands 1F",
    [0x2A01] = "Seafoam Islands B1F",
    [0x2B01] = "Seafoam Islands B2F",
    [0x2C01] = "Seafoam Islands B3F",
    [0x2D01] = "Seafoam Islands B4F",
    [0x2E01] = "Pokemon Tower 1F",
    [0x2F01] = "Pokemon Tower 2F",
    [0x3001] = "Pokemon Tower 3F",
    [0x3101] = "Pokemon Tower 4F",
    [0x3201] = "Pokemon Tower 5F",
    [0x3301] = "Pokemon Tower 6F",
    [0x3401] = "Pokemon Tower 7F",
    [0x3501] = "Power Plant",
    [0x3901] = "Victory Road 1F",
    [0x3A01] = "Victory Road 2F",
    [0x3B01] = "Victory Road 3F",
    [0x4501] = "Cerulean Cave 1F",
    [0x4601] = "Cerulean Cave 2F",
    [0x4701] = "Cerulean Cave B1F",
    [0x4801] = "Pokemon League - Lorelei",
    [0x4901] = "Pokemon League - Bruno",
    [0x4A01] = "Pokemon League - Agatha",
    [0x4B01] = "Pokemon League - Lance",
    [0x4C01] = "Pokemon League - Champion",

    -- Bank 3 (Towns and Routes)
    [0x0003] = "Pallet Town",
    [0x0103] = "Viridian City",
    [0x0203] = "Pewter City",
    [0x0303] = "Cerulean City",
    [0x0403] = "Lavender Town",
    [0x0503] = "Vermilion City",
    [0x0603] = "Celadon City",
    [0x0703] = "Fuchsia City",
    [0x0803] = "Cinnabar Island",
    [0x0903] = "Indigo Plateau",
    [0x0A03] = "Saffron City",
    [0x0C03] = "One Island",
    [0x0D03] = "Two Island",
    [0x0E03] = "Three Island",
    [0x0F03] = "Four Island",
    [0x1003] = "Five Island",
    [0x1103] = "Seven Island",
    [0x1203] = "Six Island",
    [0x1303] = "Route 1",
    [0x1403] = "Route 2",
    [0x1503] = "Route 3",
    [0x1603] = "Route 4",
    [0x1703] = "Route 5",
    [0x1803] = "Route 6",
    [0x1903] = "Route 7",
    [0x1A03] = "Route 8",
    [0x1B03] = "Route 9",
    [0x1C03] = "Route 10",
    [0x1D03] = "Route 11",
    [0x1E03] = "Route 12",
    [0x1F03] = "Route 13",
    [0x2003] = "Route 14",
    [0x2103] = "Route 15",
    [0x2203] = "Route 16",
    [0x2303] = "Route 17",
    [0x2403] = "Route 18",
    [0x2503] = "Route 19",
    [0x2603] = "Route 20",
    [0x2703] = "Route 21 North",
    [0x2803] = "Route 21 South",
    [0x2903] = "Route 22",
    [0x2A03] = "Route 23",
    [0x2B03] = "Route 24",
    [0x2C03] = "Route 25",
    [0x2D03] = "Kindle Road",
    [0x2E03] = "Treasure Beach",
    [0x2F03] = "Cape Brink",
    [0x3003] = "Bond Bridge",

    -- Bank 15 (Indoor Route 2)
    [0x000F] = "Route 2 (Viridian Forest South Gate)",
    [0x010F] = "Route 2 House",
    [0x020F] = "Route 2 East Building",
    [0x030F] = "Route 2 (Viridian Forest North Gate)",
}

local function getMapName(mapId)
    return MAP_NAMES[mapId] or string.format("Unknown (%d:%d)", mapId >> 8, mapId & 0xFF)
end

function Game.getMapId(game)
    if not game._mapAddress then return 0 end

    if game._mapAddress == true then
        local mapBank = emu:read8(0x02031DBC)
        local mapNum = emu:read8(0x02031DBD)
        return (mapNum << 8) | mapBank
    end

    if game._mapIs16 then
        return emu:read16(game._mapAddress)
    end
    return emu:read8(game._mapAddress)
end

local function simpleChecksum(s)
	local h = 0x811c9dc5
	for i = 1, #s do
		h = ((h ~ s:byte(i)) * 0x01000193) & 0xFFFFFFFF
	end
	return h
end

-- ═══════════════════════════════════════════════════════════════
-- Character maps
-- ═══════════════════════════════════════════════════════════════

local GBGameEn = Game:new{
	_terminator=0x50,
	_monNameLength=10,
	_speciesNameLength=10,
	_playerNameLength=10,
}

local GBAGameEn = Game:new{
	_terminator=0xFF,
	_monNameLength=10,
	_speciesNameLength=11,
	_playerNameLength=10,
}

local Generation1En = GBGameEn:new{
	_boxMonSize=33,
	_partyMonSize=44,
}

local Generation2En = GBGameEn:new{
	_boxMonSize=32,
	_partyMonSize=48,
}

local Generation3En = GBAGameEn:new{
	_boxMonSize=80,
	_partyMonSize=100,
}

GBGameEn._charmap = { [0]=
	"?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?",
	"?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?",
	"?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?",
	"?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?",
	"?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?",
	"", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?",
	"?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?",
	"?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", " ",
	"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P",
	"Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "(", ")", ":", ";", "[", "]",
	"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p",
	"q", "r", "s", "t", "u", "v", "w", "x", "y", "z", "e", "'d", "'l", "'s", "'t", "'v",
	"?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?",
	"?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?", "?",
	"'", "Pk", "Mn", "-", "'r", "'m", "?", "!", ".", "a", "u", "e", ">", ">", "v", "m",
	"$", "x", ".", "/", ",", "f", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"
}

GBAGameEn._charmap = { [0]=
	" ", "A", "A", "A", "C", "E", "E", "E", "E", "I", "ko", "I", "I", "O", "O", "O",
	"Oe", "U", "U", "U", "N", "ss", "a", "a", "ne", "c", "e", "e", "e", "e", "i", "ma",
	"i", "i", "o", "o", "o", "oe", "u", "u", "u", "n", "o", "a", "?", "&", "+", "a",
	"i", "u", "e", "o", "v", "=", "yo", "ga", "gi", "gu", "ge", "go", "za", "ji", "zu", "ze",
	"zo", "da", "di", "du", "de", "do", "ba", "bi", "bu", "be", "bo", "pa", "pi", "pu", "pe", "po",
	"tsu", "?", "!", "Pk", "Mn", "Po", "Ke", "?", "?", "?", "I", "%", "(", ")", "SE", "SO",
	"TA", "TI", "TU", "TE", "TO", "NA", "NI", "NU", "a", "NO", "HA", "HI", "FU", "HE", "HO", "i",
	"MI", "MU", "ME", "MO", "YA", "YU", "YO", "RA", "RI", "^", "v", "<", ">", "WO", "N", "a",
	"i", "u", "e", "o", "ya", "yu", "yo", "GA", "GI", "GU", "GE", "GO", "ZA", "ZI", "ZU", "ZE",
	"ZO", "DA", "DI", "DU", "DE", "DO", "BA", "BI", "BU", "BE", "BO", "PA", "PI", "PU", "PE", "PO",
	"TSU", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "!", "?", ".", "-", ".",
	"...", "\"", "\"", "'", "'", "m", "f", "$", ",", "x", "/", "A", "B", "C", "D", "E",
	"F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U",
	"V", "W", "X", "Y", "Z", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k",
	"l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z", ">",
	":", "A", "O", "U", "a", "o", "u", "^", "v", "<", "?", "?", "?", "?", "?", ""
}

-- ═══════════════════════════════════════════════════════════════
-- Mon readers
-- ═══════════════════════════════════════════════════════════════

function _read16BE(emu, address)
	return (emu:read8(address) << 8) | emu:read8(address + 1)
end

function Generation1En._readBoxMon(game, address, nameAddress, otAddress)
	local mon = {}
	mon.species = emu.memory.cart0:read8(game._speciesIndex + emu:read8(address + 0) - 1)
	mon.hp = _read16BE(emu, address + 1)
	mon.level = emu:read8(address + 3)
	mon.status = emu:read8(address + 4)
	mon.type1 = emu:read8(address + 5)
	mon.type2 = emu:read8(address + 6)
	mon.catchRate = emu:read8(address + 7)
	mon.moves = {
		emu:read8(address + 8),
		emu:read8(address + 9),
		emu:read8(address + 10),
		emu:read8(address + 11),
	}
	mon.otId = _read16BE(emu, address + 12)
	mon.experience = (_read16BE(emu, address + 14) << 8)| emu:read8(address + 16)
	mon.hpEV = _read16BE(emu, address + 17)
	mon.attackEV = _read16BE(emu, address + 19)
	mon.defenseEV = _read16BE(emu, address + 21)
	mon.speedEV = _read16BE(emu, address + 23)
	mon.spAttackEV = _read16BE(emu, address + 25)
	mon.spDefenseEV = mon.spAttackEV
	local iv = _read16BE(emu, address + 27)
	mon.attackIV = (iv >> 4) & 0xF
	mon.defenseIV = iv & 0xF
	mon.speedIV = (iv >> 12) & 0xF
	mon.spAttackIV = (iv >> 8) & 0xF
	mon.spDefenseIV = mon.spAttackIV
	mon.pp = {
		emu:read8(address + 28),
		emu:read8(address + 29),
		emu:read8(address + 30),
		emu:read8(address + 31),
	}
	mon.nickname = game:toString(emu:readRange(nameAddress, game._monNameLength))
	mon.otName = game:toString(emu:readRange(otAddress, game._playerNameLength))
	return mon
end

function Generation1En._readPartyMon(game, address, nameAddress, otAddress)
	local mon = game:_readBoxMon(address, nameAddress, otAddress)
	mon.level = emu:read8(address + 33)
	mon.maxHP = _read16BE(emu, address + 34)
	mon.attack = _read16BE(emu, address + 36)
	mon.defense = _read16BE(emu, address + 38)
	mon.speed = _read16BE(emu, address + 40)
	mon.spAttack = _read16BE(emu, address + 42)
	mon.spDefense = mon.spAttack
	return mon
end

function Generation2En._readBoxMon(game, address, nameAddress, otAddress)
	local mon = {}
	mon.species = emu:read8(address + 0)
	mon.item = emu:read8(address + 1)
	mon.moves = {
		emu:read8(address + 2),
		emu:read8(address + 3),
		emu:read8(address + 4),
		emu:read8(address + 5),
	}
	mon.otId = _read16BE(emu, address + 6)
	mon.experience = (_read16BE(emu, address + 8) << 8)| emu:read8(address + 10)
	mon.hpEV = _read16BE(emu, address + 11)
	mon.attackEV = _read16BE(emu, address + 13)
	mon.defenseEV = _read16BE(emu, address + 15)
	mon.speedEV = _read16BE(emu, address + 17)
	mon.spAttackEV = _read16BE(emu, address + 19)
	mon.spDefenseEV = mon.spAttackEV
	local iv = _read16BE(emu, address + 21)
	mon.attackIV = (iv >> 4) & 0xF
	mon.defenseIV = iv & 0xF
	mon.speedIV = (iv >> 12) & 0xF
	mon.spAttackIV = (iv >> 8) & 0xF
	mon.spDefenseIV = mon.spAttackIV
	mon.pp = {
		emu:read8(address + 23),
		emu:read8(address + 24),
		emu:read8(address + 25),
		emu:read8(address + 26),
	}
	mon.friendship = emu:read8(address + 27)
	mon.pokerus = emu:read8(address + 28)
	local caughtData = _read16BE(emu, address + 29)
	mon.metLocation = (caughtData >> 8) & 0x7F
	mon.metLevel = caughtData & 0x1F
	mon.level = emu:read8(address + 31)
	mon.nickname = game:toString(emu:readRange(nameAddress, game._monNameLength))
	mon.otName = game:toString(emu:readRange(otAddress, game._playerNameLength))
	return mon
end

function Generation2En._readPartyMon(game, address, nameAddress, otAddress)
	local mon = game:_readBoxMon(address, nameAddress, otAddress)
	mon.status = emu:read8(address + 32)
	mon.hp = _read16BE(emu, address + 34)
	mon.maxHP = _read16BE(emu, address + 36)
	mon.attack = _read16BE(emu, address + 38)
	mon.defense = _read16BE(emu, address + 40)
	mon.speed = _read16BE(emu, address + 42)
	mon.spAttack = _read16BE(emu, address + 44)
	mon.spDefense = _read16BE(emu, address + 46)
	return mon
end

function Generation3En._readBoxMon(game, address)
	local mon = {}
	mon.personality = emu:read32(address + 0)
	mon.otId = emu:read32(address + 4)
	mon.nickname = game:toString(emu:readRange(address + 8, game._monNameLength))
	mon.language = emu:read8(address + 18)
	local flags = emu:read8(address + 19)
	mon.isBadEgg = flags & 1
	mon.hasSpecies = (flags >> 1) & 1
	mon.isEgg = (flags >> 2) & 1
	mon.otName = game:toString(emu:readRange(address + 20, game._playerNameLength))
	mon.markings = emu:read8(address + 27)

	local key = mon.otId ~ mon.personality
	local substructSelector = {
		[ 0] = {1, 2, 3, 4}, [ 1] = {1, 2, 4, 3}, [ 2] = {1, 3, 2, 4},
		[ 3] = {1, 4, 2, 3}, [ 4] = {1, 3, 4, 2}, [ 5] = {1, 4, 3, 2},
		[ 6] = {2, 1, 3, 4}, [ 7] = {2, 1, 4, 3}, [ 8] = {3, 1, 2, 4},
		[ 9] = {4, 1, 2, 3}, [10] = {3, 1, 4, 2}, [11] = {4, 1, 3, 2},
		[12] = {2, 3, 1, 4}, [13] = {2, 4, 1, 3}, [14] = {3, 2, 1, 4},
		[15] = {4, 2, 1, 3}, [16] = {3, 4, 1, 2}, [17] = {4, 3, 1, 2},
		[18] = {2, 3, 4, 1}, [19] = {2, 4, 3, 1}, [20] = {3, 2, 4, 1},
		[21] = {4, 2, 3, 1}, [22] = {3, 4, 2, 1}, [23] = {4, 3, 2, 1},
	}

	local pSel = substructSelector[mon.personality % 24]
	local ss = {}
	for s = 1, 4 do
		ss[s] = {}
		for i = 0, 2 do
			ss[s][i] = emu:read32(address + 32 + (pSel[s] - 1) * 12 + i * 4) ~ key
		end
	end

	mon.species    = ss[1][0] & 0xFFFF
	mon.heldItem   = ss[1][0] >> 16
	mon.experience = ss[1][1]
	mon.ppBonuses  = ss[1][2] & 0xFF
	mon.friendship = (ss[1][2] >> 8) & 0xFF

	mon.moves = {
		ss[2][0] & 0xFFFF, ss[2][0] >> 16,
		ss[2][1] & 0xFFFF, ss[2][1] >> 16
	}
	mon.pp = {
		ss[2][2] & 0xFF, (ss[2][2] >> 8) & 0xFF,
		(ss[2][2] >> 16) & 0xFF, ss[2][2] >> 24
	}

	mon.hpEV       = ss[3][0] & 0xFF
	mon.attackEV   = (ss[3][0] >> 8) & 0xFF
	mon.defenseEV  = (ss[3][0] >> 16) & 0xFF
	mon.speedEV    = ss[3][0] >> 24
	mon.spAttackEV = ss[3][1] & 0xFF
	mon.spDefenseEV= (ss[3][1] >> 8) & 0xFF

	mon.pokerus      = ss[4][0] & 0xFF
	mon.metLocation  = (ss[4][0] >> 8) & 0xFF
	flags = ss[4][0] >> 16
	mon.metLevel  = flags & 0x7F
	mon.metGame   = (flags >> 7) & 0xF
	mon.pokeball  = (flags >> 11) & 0xF
	mon.otGender  = (flags >> 15) & 0x1
	flags = ss[4][1]
	mon.hpIV        = flags & 0x1F
	mon.attackIV    = (flags >> 5) & 0x1F
	mon.defenseIV   = (flags >> 10) & 0x1F
	mon.speedIV     = (flags >> 15) & 0x1F
	mon.spAttackIV  = (flags >> 20) & 0x1F
	mon.spDefenseIV = (flags >> 25) & 0x1F
	mon.altAbility  = (flags >> 31) & 1
	return mon
end

function Generation3En._readPartyMon(game, address)
	local mon = game:_readBoxMon(address)
	mon.status   = emu:read32(address + 80)
	mon.level    = emu:read8(address + 84)
	mon.hp       = emu:read16(address + 86)
	mon.maxHP    = emu:read16(address + 88)
	mon.attack   = emu:read16(address + 90)
	mon.defense  = emu:read16(address + 92)
	mon.speed    = emu:read16(address + 94)
	mon.spAttack = emu:read16(address + 96)
	mon.spDefense= emu:read16(address + 98)
	return mon
end

-- ═══════════════════════════════════════════════════════════════
-- Game definitions
-- ═══════════════════════════════════════════════════════════════

local gameRBEn = Generation1En:new{
	name="Red/Blue (USA)",
	_party=0xd16b, _partyCount=0xd163, _partyNames=0xd2b5, _partyOt=0xd273,
	_speciesNameTable=0x1c21e, _speciesIndex=0x41024,
	_badgeAddress=0xD356,
	_mapAddress=0xD35E,
}

local gameYellowEn = Generation1En:new{
	name="Yellow (USA)",
	_party=0xd16a, _partyCount=0xd162, _partyNames=0xd2b4, _partyOt=0xd272,
	_speciesNameTable=0xe8000, _speciesIndex=0x410b1,
	_badgeAddress=0xD356,
	_mapAddress=0xD35E,
}

local gameGSEn = Generation2En:new{
	name="Gold/Silver (USA)",
	_party=0xda2a, _partyCount=0xda22, _partyNames=0xdb8c, _partyOt=0xdb4a,
	_speciesNameTable=0x1b0b6a,
	_badgeAddress=0xD57C,
	_mapAddress=0xDCB5,
}

local gameCrystalEn = Generation2En:new{
	name="Crystal (USA)",
	_party=0xdcdf, _partyCount=0xdcd7, _partyNames=0xde41, _partyOt=0xddff,
	_speciesNameTable=0x5337a,
	_badgeAddress=0xD57C,
	_mapAddress=0xDCB5,
}

local gameRubyEn = Generation3En:new{
	name="Ruby (USA)",
	_party=0x3004360, _partyCount=0x3004350, _speciesNameTable=0x1f716c,
	_badgeAddress=0x20257C8,
	_mapAddress=0x3004F00, _mapIs16=true,
}

local gameRubyEnR1 = Generation3En:new{
	name="Ruby (USA) Rev1",
	_party=0x3004360, _partyCount=0x3004350, _speciesNameTable=0x1f7184,
	_badgeAddress=0x20257C8, _mapAddress=0x3004F00, _mapIs16=true,
}

local gameSapphireEn = Generation3En:new{
	name="Sapphire (USA)",
	_party=0x3004360, _partyCount=0x3004350, _speciesNameTable=0x1f70fc,
	_badgeAddress=0x20257C8, _mapAddress=0x3004F00, _mapIs16=true,
}

local gameSapphireEnR1 = Generation3En:new{
	name="Sapphire (USA) Rev1",
	_party=0x3004360, _partyCount=0x3004350, _speciesNameTable=0x1f7114,
	_badgeAddress=0x20257C8, _mapAddress=0x3004F00, _mapIs16=true,
}

local gameEmeraldEn = Generation3En:new{
	name="Emerald (USA)",
	_party=0x20244ec, _partyCount=0x20244e9, _speciesNameTable=0x3185c8,
	_badgeAddress=0x20257C8, _mapAddress=0x3004F00, _mapIs16=true,
}

local gameFireRedEn = Generation3En:new{
    name="FireRed (USA)",
    _party=0x2024284, _partyCount=0x2024029, _speciesNameTable=0x245ee0,
    _badgeAddress=true,
    _mapAddress=true, _mapIs16=false,
}

local gameFireRedEnR1 = gameFireRedEn:new{
	name="FireRed (USA) Rev1", _speciesNameTable=0x245f50,
}

local gameLeafGreenEn = Generation3En:new{
	name="LeafGreen (USA)",
	_party=0x2024284, _partyCount=0x2024029, _speciesNameTable=0x245ebc,
	_badgeAddress=0x20257C8, _mapAddress=0x3004F00, _mapIs16=true,
}

local gameLeafGreenEnR1 = gameLeafGreenEn:new{
	name="LeafGreen (USA) Rev1", _speciesNameTable=0x245f2c,
}

local gameCodes = {
	[C.PLATFORM.GB] = {
		["AAUE"] = gameGSEn,
		["AAXE"] = gameGSEn,
		["BYTE"] = gameCrystalEn,
	},
	[C.PLATFORM.GBA] = {
		["AXVE"] = gameRubyEn,
		["AXPE"] = gameSapphireEn,
		["BPEE"] = gameEmeraldEn,
		["BPRE"] = gameFireRedEn,
		["BPGE"] = gameLeafGreenEn,
	}
}

local gameCrc32 = {
	[0x9f7fdd53] = gameRBEn,
	[0xd6da8a1a] = gameRBEn,
	[0x7d527d62] = gameYellowEn,
	[0x84ee4776] = gameFireRedEnR1,
	[0xdaffecec] = gameLeafGreenEnR1,
	[0x61641576] = gameRubyEnR1,
	[0xaeac73e6] = gameRubyEnR1,
	[0xbafedae5] = gameSapphireEnR1,
	[0x9cc4410e] = gameSapphireEnR1,
}

-- ═══════════════════════════════════════════════════════════════
-- Nuzlocke state
-- ═══════════════════════════════════════════════════════════════

local nuzlocke = {

	encounters  = {},

	deathLog    = {},

	prevParty   = {},
	prevBadges  = 0,
	frameCount  = 0,
	runId       = nil,
	snapshotSeq = 0,
}


local function monKey(mon)
	return string.format("%d:%s", mon.otId, mon.nickname)
end

local function detectDeaths(party, mapId)
	local currentKeys = {}
	for _, mon in ipairs(party) do
		local k = monKey(mon)
		currentKeys[k] = true
		if mon.hp == 0 and nuzlocke.prevParty[k] and nuzlocke.prevParty[k].hp > 0 then
			local entry = {
				nickname  = mon.nickname,
				species   = mon.speciesName,
				level     = mon.level,
				location  = mapId,
				frameCount= nuzlocke.frameCount,
			}
			table.insert(nuzlocke.deathLog, entry)
			console:log(string.format("[NUZLOCKE] DEATH: %s (%s) Lv%d at map %d",
				mon.nickname, mon.speciesName, mon.level, mapId))
		end
	end
end


local function trackEncounter(party, mapId)
    if nuzlocke.encounters[mapId] then return end

    for _, mon in ipairs(party) do
        local meta = mon.metLocation
        if meta and meta == mapId and not nuzlocke.prevParty[monKey(mon)] then
            nuzlocke.encounters[mapId] = mon.speciesName
            console:log(string.format("[NUZLOCKE] ENCOUNTER on map %d: %s", mapId, mon.speciesName))
            return
        end
    end
end

-- ─── Encounter tracking ───────────────────────────────────────────────────────

local ENCOUNTER_STATUS = {
    NONE = "none",
    IN_BATTLE = "in_battle",
    CAUGHT = "caught",
    FAILED = "failed"
}


nuzlocke.encounterStatus = {}

local function isInWildBattle()
    local battleFlags = emu:read32(0x02022B4C)
    local battleType = emu:read16(0x020386AC)
    return battleFlags == 0x04 and battleType == 0
end


local prevInBattle = false
local battleStartMap = nil
local battleEnemy = nil
local lastOverworldMap = 0
local partyAtBattleStart = {}

local partyAtBattleStart = {}

local function updateEncounterTracking(party, mapId)
    local inBattle = isInWildBattle()

    if inBattle ~= prevInBattle then
        console:log(string.format("[STATE] inBattle changed: %s -> %s",
            tostring(prevInBattle), tostring(inBattle)))
    end

    if not inBattle and mapId ~= 0 then
        lastOverworldMap = mapId
        console:log(string.format("[MAP] Updated lastOverworldMap: %d (%s)",
            lastOverworldMap, getMapName(lastOverworldMap)))
    end

    if nuzlocke.frameCount % 60 == 0 then
        console:log(string.format("[ENCOUNTER] inBattle: %s, mapId: %d, prevInBattle: %s",
            tostring(inBattle), mapId, tostring(prevInBattle)))
    end

    if inBattle and not prevInBattle then

        console:log(string.format("[POKEDEX] hasPokedex: %s", tostring(hasPokedex())))

        partyAtBattleStart = {}
        for _, mon in ipairs(party) do
            partyAtBattleStart[monKey(mon)] = true
        end

        console:log(string.format("[ENCOUNTER] Battle started on map %d (%s)!",
                lastOverworldMap, getMapName(lastOverworldMap)))
            battleStartMap = lastOverworldMap

        local battleType = emu:read16(0x020386AC)
        if battleType ~= 0 then
            console:log("[ENCOUNTER] Trainer battle, skipping")
            prevInBattle = inBattle
            return
        end

        if not hasPokedex() then
            console:log("[ENCOUNTER] No Pokedex yet ,not recording encounter")
            battleStartMap = nil
            prevInBattle = inBattle
            return
        end

        if not nuzlocke.encounterStatus[lastOverworldMap] then
            local enemyMon = game:_readBoxMon(0x0202402C)
            local enemySpecies = "Unknown"
            if enemyMon and enemyMon.species and enemyMon.species > 0 and enemyMon.species < 252 then
                enemySpecies = game:getSpeciesName(enemyMon.species)
            end
            battleEnemy = enemySpecies
            nuzlocke.encounterStatus[lastOverworldMap] = {
                status = ENCOUNTER_STATUS.IN_BATTLE,
                species = enemySpecies,
                nickname = nil
            }
            console:log(string.format("[NUZLOCKE] New encounter on map %d: %s",
                lastOverworldMap, enemySpecies))
        end

    end

    if not inBattle and prevInBattle then
        console:log(string.format("[ENCOUNTER] Battle ended on map %d (%s)!",
            battleStartMap or 0, getMapName(battleStartMap or 0)))

        if battleStartMap ~= nil then
            local enc = nuzlocke.encounterStatus[battleStartMap]
            if enc and enc.status == ENCOUNTER_STATUS.IN_BATTLE then
                local caught = false
                for _, mon in ipairs(party) do
                    if not partyAtBattleStart[monKey(mon)] then
                        enc.status = ENCOUNTER_STATUS.CAUGHT
                        enc.nickname = mon.nickname
                        enc.species = game:getSpeciesName(mon.species)
                        caught = true
                        console:log(string.format("[NUZLOCKE] Caught %s (%s) on map %d",
                            mon.nickname, enc.species, battleStartMap))
                        break
                    end
                end
                if not caught then
                    enc.status = ENCOUNTER_STATUS.FAILED
                    console:log(string.format("[NUZLOCKE] Failed encounter on map %d: %s got away",
                        battleStartMap, enc.species or "?"))
                end
            end
            battleStartMap = nil
            battleEnemy = nil
        end
    end

    prevInBattle = inBattle
end

-- ═══════════════════════════════════════════════════════════════
-- Message formatting
-- ═══════════════════════════════════════════════════════════════

local function buildSnapshotPayload(party, badges, mapId)
	nuzlocke.snapshotSeq = nuzlocke.snapshotSeq + 1
	local lines = {}
	table.insert(lines, string.format("SEQ:%d", nuzlocke.snapshotSeq))
	table.insert(lines, string.format("FRAME:%d", nuzlocke.frameCount))
	table.insert(lines, string.format("MAP:%d", mapId))
	table.insert(lines, string.format("BADGES:%d", badges))

	for i, mon in ipairs(party) do
		table.insert(lines, string.format("MON:%d|%s|%s|%d|%d|%d",
			i,
			mon.nickname,
			mon.speciesName,
			mon.level,
			mon.hp,
			mon.maxHP))
	end

	for _, d in ipairs(nuzlocke.deathLog) do
		table.insert(lines, string.format("DEAD|%s|%s|%d|%d|%d",
			d.nickname, d.species, d.level, d.location, d.frameCount))
	end

    for mapIdEnc, specName in pairs(nuzlocke.encounters) do
        table.insert(lines, string.format("ENC|%d|%s", mapIdEnc, specName))
    end

    for mapIdEnc, enc in pairs(nuzlocke.encounterStatus) do
        table.insert(lines, string.format("ENCV2|%d|%s|%s|%s",
            mapIdEnc,
            enc.species or "?",
            enc.nickname or "",
            enc.status
        ))
    end

	return table.concat(lines, "\n")
end

local function buildFullMessage(party, badges, mapId)
	local payload = buildSnapshotPayload(party, badges, mapId)
	local checksum = simpleChecksum(payload)
	return string.format("SNAPSHOT_BEGIN\n%s\nCHECKSUM:%d\nSNAPSHOT_END\n",
		payload, checksum)
end

-- ═══════════════════════════════════════════════════════════════
-- Main callbacks
-- ═══════════════════════════════════════════════════════════════

function printPartyStatus(gameObj, buffer)
	buffer:clear()
	for _, mon in ipairs(gameObj:getParty()) do
		buffer:print(string.format("%-10s (Lv%3i %10s): %3i/%3i\n",
			mon.nickname, mon.level, gameObj:getSpeciesName(mon.species),
			mon.hp, mon.maxHP))
	end
end

function detectGame()
	local checksum = 0
	for i, v in ipairs({ emu:checksum(C.CHECKSUM.CRC32):byte(1, 4) }) do
		checksum = checksum * 256 + v
	end
	game = gameCrc32[checksum]
	if not game then
		game = gameCodes[emu:platform()][emu:getGameCode()]
	end
	if not game then
		console:error("Unknown game!")
	else
		console:log("Found game: " .. game.name)
        if not partyBuffer then
            partyBuffer = console:createBuffer("Party")
        end

		nuzlocke.encounters  = {}
		nuzlocke.deathLog    = {}
		nuzlocke.prevParty   = {}
		nuzlocke.prevBadges  = 0
		nuzlocke.frameCount  = 0
		nuzlocke.snapshotSeq = 0
	end
end


local SNAPSHOT_EVERY = 60
local framesSinceSnapshot = 0

local function toBinary(num)
    local t = {}
    for i = 7, 0, -1 do
        local bit = (num >> i) & 1
        table.insert(t, bit)
    end
    return table.concat(t)
end

local function hasPokeballs()
    local bagBase = emu:read32(0x03005008) + 0x560
    local ballPocketCount = emu:read16(bagBase + 0x640)
    return ballPocketCount > 0
end

function hasPokedex()
    return FlagGet(0x829)
end

function updateBuffer()
	if not game or not partyBuffer then return end

	nuzlocke.frameCount = nuzlocke.frameCount + 1
	framesSinceSnapshot = framesSinceSnapshot + 1

	local party    = game:getParty()
	local mapId    = game:getMapId()
	local badges   = game:getBadges()

    for _, mon in ipairs(party) do
        mon.speciesName = game:getSpeciesName(mon.species)
    end

	-- Nuzlocke logic (runs every frame for accuracy)
    detectDeaths(party, mapId)
    updateEncounterTracking(party, mapId)

    -- Badge gain notification

    if badges ~= nuzlocke.prevBadges then
        console:log(string.format(
            "[NUZLOCKE] Badge change: %s -> %s",
            toBinary(nuzlocke.prevBadges),
            toBinary(badges)
        ))
        nuzlocke.prevBadges = badges

        -- Send snapshot on badge gain
        local msg = buildFullMessage(party, badges, mapId)
        for id, sock in pairs(ST_sockets) do
            if sock then sock:send(msg) end
        end
        framesSinceSnapshot = 0
        console:log("[NUZLOCKE] Immediate snapshot sent for badge gain")
    end

	-- Build prevParty map for next frame
	nuzlocke.prevParty = {}
	for _, mon in ipairs(party) do
		nuzlocke.prevParty[monKey(mon)] = mon
	end

	-- Update the mGBA buffer
	printPartyStatus(game, partyBuffer)

	-- Send snapshot over socket on throttle interval
	if framesSinceSnapshot >= SNAPSHOT_EVERY and next(ST_sockets) then
		framesSinceSnapshot = 0
		local msg = buildFullMessage(party, badges, mapId)
		for id, sock in pairs(ST_sockets) do
			if sock then sock:send(msg) end
		end
	end
end

callbacks:add("start", detectGame)
callbacks:add("frame", updateBuffer)
if emu then detectGame() end

-- ═══════════════════════════════════════════════════════════════
-- Socket server
-- ═══════════════════════════════════════════════════════════════

lastkeys  = nil
server    = nil
ST_sockets= {}
nextID    = 1

local KEY_NAMES = { "A", "B", "s", "S", "<", ">", "^", "v", "R", "L" }

function ST_stop(id)
	local sock = ST_sockets[id]
	ST_sockets[id] = nil
	sock:close()
end

function ST_format(id, msg, isError)
	local prefix = "Socket " .. id
	if isError then prefix = prefix .. " Error: "
	else prefix = prefix .. " Received: " end
	return prefix .. msg
end

function ST_error(id, err)
	console:error(ST_format(id, err, true))
	ST_stop(id)
end

function ST_handleMessage(id, msg)
	msg = msg:match("^(.-)%s*$")
	if msg:sub(1, 6) == "HELLO:" then
		nuzlocke.runId = msg:sub(7)
		console:log("[NUZLOCKE] Run ID set: " .. nuzlocke.runId)
		local sock = ST_sockets[id]
		if sock then sock:send("HELLO_ACK:" .. nuzlocke.runId .. "\n") end
	elseif msg:sub(1, 4) == "ACK:" then
		local seq = tonumber(msg:sub(5))
		console:log(string.format("[NUZLOCKE] Kotlin ACK seq %d", seq or -1))
	elseif msg == "PING" then
		local sock = ST_sockets[id]
		if sock then sock:send("PONG\n") end
	else
		console:log(ST_format(id, msg))
	end
end

function ST_received(id)
	local sock = ST_sockets[id]
	if not sock then return end
	while true do
		local p, err = sock:receive(1024)
		if p then
			ST_handleMessage(id, p)
		else
			if err ~= socket.ERRORS.AGAIN then
				console:error(ST_format(id, err, true))
				ST_stop(id)
			end
			return
		end
	end
end

function ST_scankeys()
	local keys = emu:getKeys()
	if keys ~= lastkeys then
		lastkeys = keys
		local msg = "["
		for i, k in ipairs(KEY_NAMES) do
			if (keys & (1 << (i - 1))) == 0 then msg = msg .. " "
			else msg = msg .. k end
		end
		msg = msg .. "]\n"
		for id, sock in pairs(ST_sockets) do
			if sock then sock:send(msg) end
		end
	end
end

function ST_accept()
	local sock, err = server:accept()
	if err then
		console:error(ST_format("Accept", err, true))
		return
	end
	local id = nextID
	nextID = id + 1
	ST_sockets[id] = sock
	sock:add("received", function() ST_received(id) end)
	sock:add("error",    function() ST_error(id) end)
	console:log(ST_format(id, "Connected"))
	sock:send("NUZLOCKE_TRACKER v1\n")
end

callbacks:add("keysRead", ST_scankeys)

local port = 8888
server = nil
while not server do
	server, err = socket.bind(nil, port)
	if err then
		if err == socket.ERRORS.ADDRESS_IN_USE then
			port = port + 1
		else
			console:error(ST_format("Bind", err, true))
			break
		end
	else
		local ok
		ok, err = server:listen()
		if err then
			server:close()
			console:error(ST_format("Listen", err, true))
		else
			console:log("Nuzlocke Tracker: Listening on port " .. port)
			server:add("received", ST_accept)
		end
	end
end