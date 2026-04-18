# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ajLeaderboards is a Minecraft Bukkit/Spigot plugin that provides leaderboard functionality via PlaceholderAPI. It tracks player statistics from any PAPI placeholder, caches them in a database, and displays rankings through signs, heads, armor stands, and PAPI placeholders. Supports Folia.

## Build Commands

```bash
./gradlew shadowJar        # Build the plugin JAR (output: build/libs/ajLeaderboards.jar)
./gradlew test             # Run tests (JUnit 4 for main, JUnit 5 for NMS modules)
./gradlew clean shadowJar  # Clean build
```

The project uses both ShadowJar (for dependency shading/relocation) and SlimJar (for runtime dependency downloading of HikariCP, H2, and OkHttp).

## Architecture

### Multi-module Gradle project
- **Root module**: Main plugin code (`us.ajg0702.leaderboards`)
- **nms/nms-legacy**: Base NMS abstraction (HeadUtils for player head rendering)
- **nms/nms-19**: 1.19+ implementation using PlayerProfile API

### Core data flow
1. **Cache** (`cache/Cache.java`) - Database persistence layer. Manages board tables, player stat updates, and timed resets (daily/weekly/monthly). Dual-writes to both DB and in-memory caches.
2. **TopManager** (`boards/TopManager.java`) - Multi-layered Guava LoadingCache system with async fetch executor. Caches positions, stat entries, board sizes, and totals with configurable refresh intervals.
3. **CacheMethod** interface (`cache/methods/`) - Pluggable storage backends:
   - **H2Method** (default, embedded) 
   - **MysqlMethod** (HikariCP connection pool)
   - **SqliteMethod** (deprecated, migrates to H2)

### Key subsystems
- **displays/**: Physical leaderboard displays - signs, heads, armor stands, LuckPerms context integration
- **placeholders/**: PAPI expansion with sub-packages for `lb/` (leaderboard), `player/`, `relative/`, and `debug/` placeholders
- **commands/**: Uses `us.ajg0702.commands` framework. Main command `/ajleaderboards` (aliases: `/ajlb`, `/ajl`, `/alb`) with subcommands
- **formatting/**: Number/time formatting with pluggable format types (`formats/`)
- **boards/**: Data models including `StatEntry`, `TimedType` (ALLTIME/DAILY/WEEKLY/MONTHLY), board keys

### Dependencies (shaded via relocation)
- **Adventure API** (MiniMessage) for text components
- **ajUtils** (`us.ajg0702.utils`) for Config, Messages, UpdateManager, Folia-compatible scheduler
- **PaperLib** for async chunk loading
- **Configurate** (YAML) for config/messages

### Important patterns
- Java 8 source compatibility target
- All DB and stat operations run asynchronously via `CompatScheduler`
- Plugin does NOT support `/reload` - throws IllegalStateException if attempted
- Version token `@VERSION@` in `plugin.yml` is replaced during resource processing
