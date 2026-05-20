# Graph Report - .  (2026-05-08)

## Corpus Check
- Corpus is ~40,000 words - fits in a single context window. You may not need a graph.

## Summary
- 1064 nodes · 2150 edges · 91 communities (21 shown, 70 thin omitted)
- Extraction: 64% EXTRACTED · 36% INFERRED · 0% AMBIGUOUS · INFERRED: 771 edges (avg confidence: 0.8)
- Token cost: 22,358 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Cache & Database Layer|Cache & Database Layer]]
- [[_COMMUNITY_Armor Stand Displays|Armor Stand Displays]]
- [[_COMMUNITY_TopManager Caching|TopManager Caching]]
- [[_COMMUNITY_Leaderboard GUI|Leaderboard GUI]]
- [[_COMMUNITY_Metrics & Plugin Init|Metrics & Plugin Init]]
- [[_COMMUNITY_CacheMethod Interface|CacheMethod Interface]]
- [[_COMMUNITY_UnclosableConnection Wrapper|UnclosableConnection Wrapper]]
- [[_COMMUNITY_Plugin Core & Redis|Plugin Core & Redis]]
- [[_COMMUNITY_StatEntry Formatting|StatEntry Formatting]]
- [[_COMMUNITY_StatEntry Accessors|StatEntry Accessors]]
- [[_COMMUNITY_Number Format System|Number Format System]]
- [[_COMMUNITY_LuckPerms Context|LuckPerms Context]]
- [[_COMMUNITY_Debug System|Debug System]]
- [[_COMMUNITY_Placeholder Parsing|Placeholder Parsing]]
- [[_COMMUNITY_Event System|Event System]]
- [[_COMMUNITY_Timed Reset Logic|Timed Reset Logic]]
- [[_COMMUNITY_Sign Commands|Sign Commands]]
- [[_COMMUNITY_Config & Architecture Docs|Config & Architecture Docs]]
- [[_COMMUNITY_GUI Score Display|GUI Score Display]]
- [[_COMMUNITY_LB Value Placeholders|LB Value Placeholders]]
- [[_COMMUNITY_Sign Management|Sign Management]]
- [[_COMMUNITY_Sign Add & Board Types|Sign Add & Board Types]]
- [[_COMMUNITY_Score Formatting Tests|Score Formatting Tests]]
- [[_COMMUNITY_PlayerBoardType Keys|PlayerBoardType Keys]]
- [[_COMMUNITY_Offline Updater|Offline Updater]]
- [[_COMMUNITY_PositionBoardType Keys|PositionBoardType Keys]]
- [[_COMMUNITY_BoardPlayer Model|BoardPlayer Model]]
- [[_COMMUNITY_BoardType Keys|BoardType Keys]]
- [[_COMMUNITY_Format Unit Tests|Format Unit Tests]]
- [[_COMMUNITY_ArmorStand Request|ArmorStand Request]]
- [[_COMMUNITY_ExtraKey Model|ExtraKey Model]]
- [[_COMMUNITY_Export Command|Export Command]]
- [[_COMMUNITY_LuckPerms Context Loader|LuckPerms Context Loader]]
- [[_COMMUNITY_Rolling Debug|Rolling Debug]]
- [[_COMMUNITY_Format Interface|Format Interface]]
- [[_COMMUNITY_Viewer Command|Viewer Command]]
- [[_COMMUNITY_Remove Sign Command|Remove Sign Command]]
- [[_COMMUNITY_Remove Board Command|Remove Board Command]]
- [[_COMMUNITY_Add Board Command|Add Board Command]]
- [[_COMMUNITY_Version Command|Version Command]]
- [[_COMMUNITY_Time Debug Command|Time Debug Command]]
- [[_COMMUNITY_Save Debug Command|Save Debug Command]]
- [[_COMMUNITY_Legacy Debug Wrapper|Legacy Debug Wrapper]]
- [[_COMMUNITY_Legacy Head Utils|Legacy Head Utils]]
- [[_COMMUNITY_Relative Color Placeholder|Relative Color Placeholder]]
- [[_COMMUNITY_LB Value Formatted|LB Value Formatted]]
- [[_COMMUNITY_Relative Display Name|Relative Display Name]]
- [[_COMMUNITY_Debug Fetching Placeholder|Debug Fetching Placeholder]]
- [[_COMMUNITY_Relative Value Formatted|Relative Value Formatted]]
- [[_COMMUNITY_LB Raw Value|LB Raw Value]]
- [[_COMMUNITY_LB UUID Placeholder|LB UUID Placeholder]]
- [[_COMMUNITY_Player Time Placeholder|Player Time Placeholder]]
- [[_COMMUNITY_Player Value Formatted|Player Value Formatted]]
- [[_COMMUNITY_Relative Raw Value|Relative Raw Value]]
- [[_COMMUNITY_Relative Suffix|Relative Suffix]]
- [[_COMMUNITY_Size Placeholder|Size Placeholder]]
- [[_COMMUNITY_Total Formatted|Total Formatted]]
- [[_COMMUNITY_LB Suffix Placeholder|LB Suffix Placeholder]]
- [[_COMMUNITY_LB Color Placeholder|LB Color Placeholder]]
- [[_COMMUNITY_Total Placeholder|Total Placeholder]]
- [[_COMMUNITY_LB Time Placeholder|LB Time Placeholder]]
- [[_COMMUNITY_LB Display Name|LB Display Name]]
- [[_COMMUNITY_Relative Position|Relative Position]]
- [[_COMMUNITY_Player Value Time|Player Value Time]]
- [[_COMMUNITY_Relative Value|Relative Value]]
- [[_COMMUNITY_Relative Prefix|Relative Prefix]]
- [[_COMMUNITY_Player Position Formatted|Player Position Formatted]]
- [[_COMMUNITY_Reset Placeholder|Reset Placeholder]]
- [[_COMMUNITY_Total Raw Placeholder|Total Raw Placeholder]]
- [[_COMMUNITY_LB Name Placeholder|LB Name Placeholder]]
- [[_COMMUNITY_Relative Time|Relative Time]]
- [[_COMMUNITY_LB Prefix Placeholder|LB Prefix Placeholder]]
- [[_COMMUNITY_Relative Name|Relative Name]]
- [[_COMMUNITY_LB Extra Placeholder|LB Extra Placeholder]]
- [[_COMMUNITY_Player Extra Placeholder|Player Extra Placeholder]]
- [[_COMMUNITY_Player Value Placeholder|Player Value Placeholder]]
- [[_COMMUNITY_Relative Extra|Relative Extra]]
- [[_COMMUNITY_LB Skin Placeholder|LB Skin Placeholder]]
- [[_COMMUNITY_Import Command|Import Command]]
- [[_COMMUNITY_Remove Player Command|Remove Player Command]]
- [[_COMMUNITY_Update Command|Update Command]]
- [[_COMMUNITY_Reset Command|Reset Command]]
- [[_COMMUNITY_Update All Offline|Update All Offline]]
- [[_COMMUNITY_Debug Format Command|Debug Format Command]]
- [[_COMMUNITY_Regex Test|Regex Test]]
- [[_COMMUNITY_Project Documentation|Project Documentation]]
- [[_COMMUNITY_Redis & Profile Config|Redis & Profile Config]]

## God Nodes (most connected - your core abstractions)
1. `UnClosableConnection` - 45 edges
2. `LeaderboardPlugin` - 39 edges
3. `Cache` - 32 edges
4. `of()` - 32 edges
5. `TopManager` - 32 edges
6. `StatEntry` - 32 edges
7. `lowerName()` - 31 edges
8. `warning()` - 23 edges
9. `getBoolean()` - 23 edges
10. `BoardSign` - 18 edges

## Surprising Connections (you probably didn't know these)
- `Release Update Checklist` --conceptually_related_to--> `ajLeaderboards Project Overview`  [INFERRED]
  update.md → CLAUDE.md
- `ajLeaderboards Config Settings` --conceptually_related_to--> `TopManager Guava LoadingCache System`  [INFERRED]
  src/main/resources/config.yml → CLAUDE.md
- `Cache Storage Configuration` --conceptually_related_to--> `CacheMethod Pluggable Storage Interface`  [INFERRED]
  src/main/resources/cache_storage.yml → CLAUDE.md
- `Redis Leaderboard Cache Feature` --conceptually_related_to--> `TopManager Guava LoadingCache System`  [INFERRED]
  src/main/resources/config.yml → CLAUDE.md
- `PAPI Expansion Placeholders Subsystem` --references--> `PlaceholderAPI Dependency`  [INFERRED]
  CLAUDE.md → src/main/resources/plugin.yml

## Hyperedges (group relationships)
- **Core Data Flow: TopManager -> Cache -> CacheMethod** — claude_topmanager, claude_cache_java, claude_cachemethod, claude_h2method, claude_mysqlmethod [EXTRACTED 1.00]
- **Redis Caching: UUID Lookup + Leaderboard Cache + Profile Command** — config_redis_uuid_lookup, config_redis_cache, plugin_profile_command, plugin_leaderboard_command [EXTRACTED 1.00]
- **Storage Backend Selection via cache_storage.yml** — cache_storage_yml, claude_h2method, claude_mysqlmethod, claude_cachemethod [EXTRACTED 1.00]

## Communities (91 total, 70 thin omitted)

### Community 0 - "Cache & Database Layer"
Cohesion: 0.05
Nodes (11): lowerName(), Cache, ExtraManager, PlaceholderFormatter, DbRow, ProcessLogger, EasyJsonObject, Exporter (+3 more)

### Community 1 - "Armor Stand Displays"
Cohesion: 0.05
Nodes (7): ArmorStandCache, ArmorStandManager, HeadManager, CachedData, HeadUtils, BoardSign, SignManager

### Community 2 - "TopManager Caching"
Cohesion: 0.08
Nodes (11): load(), reload(), TopManager, getBoolean(), isBoolean(), shouldBlock(), warning(), ThreadFactoryProxy (+3 more)

### Community 3 - "Leaderboard GUI"
Cohesion: 0.05
Nodes (10): CategoryDef, LeaderboardGUI, LeaderboardGUIListener, LeaderboardHolder, CachedEntry, LeaderboardRedisCache, ProfileHolder, InventoryHolder (+2 more)

### Community 4 - "Metrics & Plugin Init"
Cohesion: 0.07
Nodes (13): CustomChart, AdvancedBarChart, AdvancedPie, CustomChart, DrilldownPie, JsonObject, JsonObjectBuilder, Metrics (+5 more)

### Community 5 - "CacheMethod Interface"
Cohesion: 0.05
Nodes (7): CacheMethod, CacheMethod, Connection, UUIDLookup, H2Method, MysqlMethod, SqliteMethod

### Community 10 - "Number Format System"
Cohesion: 0.09
Nodes (6): BaseCommand, Format, ColonTime, Default, Time, MainCommand

### Community 11 - "LuckPerms Context"
Cohesion: 0.09
Nodes (9): JavaPlugin, error(), severe(), warn(), load(), PositionContext, WithLPCtx, WithoutLPCtx (+1 more)

### Community 12 - "Debug System"
Cohesion: 0.13
Nodes (5): DebugWrapper, Debug, HeadUtils19, Reload, VersionedHeadUtils

### Community 13 - "Placeholder Parsing"
Cohesion: 0.11
Nodes (3): CachedPlaceholder, Placeholder, PlaceholderExpansion

### Community 14 - "Event System"
Cohesion: 0.14
Nodes (4): Cancellable, Event, PreTimedTypeResetEvent, UpdatePlayerEvent

### Community 15 - "Timed Reset Logic"
Cohesion: 0.13
Nodes (4): getEstimatedLastReset(), getNextReset(), Resets, TimeUtils

### Community 16 - "Sign Commands"
Cohesion: 0.16
Nodes (5): Signs, SubCommand, CheckUpdate, ForceUpdate, UpdatePlayer

### Community 17 - "Config & Architecture Docs"
Cohesion: 0.14
Nodes (16): Cache Storage Configuration, Async DB Operations via CompatScheduler, Cache Database Persistence Layer, CacheMethod Pluggable Storage Interface, Physical Leaderboard Displays Subsystem, Folia Server Support, H2Method Default Embedded Storage, MysqlMethod HikariCP Storage (+8 more)

### Community 19 - "LB Value Placeholders"
Cohesion: 0.19
Nodes (4): Value, Placeholder, PlayerPosition, PlayerValueRaw

### Community 21 - "Sign Add & Board Types"
Cohesion: 0.21
Nodes (3): lowerNames(), AddSign, ListCommand

### Community 28 - "Format Unit Tests"
Cohesion: 0.38
Nodes (3): ColonTimeTest, TimeTest, TestCase

## Knowledge Gaps
- **8 isolated node(s):** `ajLeaderboards Project Overview`, `Release Update Checklist`, `Physical Leaderboard Displays Subsystem`, `PAPI Expansion Placeholders Subsystem`, `Redis UUID Lookup Feature` (+3 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **70 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `UnClosableConnection` connect `UnclosableConnection Wrapper` to `Cache & Database Layer`, `CacheMethod Interface`?**
  _High betweenness centrality (0.117) - this node is a cross-community bridge._
- **Why does `lowerName()` connect `Cache & Database Layer` to `CacheMethod Interface`, `Plugin Core & Redis`, `LuckPerms Context`, `Timed Reset Logic`, `GUI Score Display`, `Sign Management`, `Sign Add & Board Types`, `Score Formatting Tests`, `BoardType Keys`?**
  _High betweenness centrality (0.057) - this node is a cross-community bridge._
- **Why does `PlaceholderExpansion` connect `Placeholder Parsing` to `Metrics & Plugin Init`?**
  _High betweenness centrality (0.053) - this node is a cross-community bridge._
- **What connects `ajLeaderboards Project Overview`, `Release Update Checklist`, `Physical Leaderboard Displays Subsystem` to the rest of the system?**
  _8 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Cache & Database Layer` be split into smaller, more focused modules?**
  _Cohesion score 0.05 - nodes in this community are weakly interconnected._
- **Should `Armor Stand Displays` be split into smaller, more focused modules?**
  _Cohesion score 0.05 - nodes in this community are weakly interconnected._
- **Should `TopManager Caching` be split into smaller, more focused modules?**
  _Cohesion score 0.08 - nodes in this community are weakly interconnected._