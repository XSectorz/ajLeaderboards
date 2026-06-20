package us.ajg0702.leaderboards.cache;

import com.google.common.collect.ImmutableMap;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.spongepowered.configurate.ConfigurateException;
import us.ajg0702.leaderboards.Debug;
import us.ajg0702.leaderboards.LeaderboardPlugin;
import us.ajg0702.leaderboards.api.events.PreTimedTypeResetEvent;
import us.ajg0702.leaderboards.api.events.UpdatePlayerEvent;
import us.ajg0702.leaderboards.boards.StatEntry;
import us.ajg0702.leaderboards.boards.TimedType;
import us.ajg0702.leaderboards.boards.keys.BoardType;
import us.ajg0702.leaderboards.boards.keys.PositionBoardType;
import us.ajg0702.leaderboards.cache.helpers.DbRow;
import us.ajg0702.leaderboards.cache.methods.H2Method;
import us.ajg0702.leaderboards.cache.methods.MysqlMethod;
import us.ajg0702.leaderboards.cache.methods.SqliteMethod;
import us.ajg0702.leaderboards.utils.BoardPlayer;
import us.ajg0702.leaderboards.utils.Partition;
import us.ajg0702.utils.common.ConfigFile;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

@SuppressWarnings("FieldCanBeLocal")
public class Cache {
	private String q = "'";

	private final String SELECT_POSITION = "select 'id','value','namecache','prefixcache','suffixcache','displaynamecache',"+deltaBuilder()+" from '%s' order by '%s' %s, namecache desc limit 1 offset %d";
	private final String SELECT_PLAYER = "select 'id','value','namecache','prefixcache','suffixcache','displaynamecache',"+deltaBuilder()+" from '%s' order by '%s' %s, namecache desc";
	private final String GET_POSITION = "/*%s*/with N as (select *,ROW_NUMBER() OVER (order by '%s' %s, namecache desc) as position from '%s') select 'id','value','namecache','prefixcache','suffixcache','displaynamecache',position,"+deltaBuilder()+" from N where 'id'=?";
	/**
	 * Batched top-N query — returns positions 1..N in one round-trip.
	 *
	 * <p>Replaces the old pattern of 10 separate {@link #SELECT_POSITION}
	 * calls (each with {@code LIMIT 1 OFFSET N}). The optimiser can now
	 * walk the {@code (sortcol, namecache)} composite index just once
	 * and stream out the first N rows — no per-call overhead, no
	 * repeated index traversals, no JDBC round-trip × N.
	 *
	 * <p>Saves ~10× the latency on top-10 fetches that previously fired
	 * 10 queries serially during {@code refreshAll} and the snapshot
	 * refresh.
	 */
	private final String SELECT_TOP_N = "select 'id','value','namecache','prefixcache','suffixcache','displaynamecache',"+deltaBuilder()+" from '%s' order by '%s' %s, namecache desc limit %d";
	/**
	 * Faster rank lookup — uses a COUNT-based ranking instead of the
	 * {@code ROW_NUMBER() OVER ()} CTE in {@link #GET_POSITION}.
	 *
	 * <p>The old CTE materialised positions for EVERY row before
	 * filtering to the player — O(N) work even with indexes, and on a
	 * 10k-player board that's 10k row reads per call. The new form uses
	 * the index on the sort column to count rows ranked above the
	 * player ({@code value > x}, with a secondary clause for
	 * {@code namecache} ties at the same value) — typically O(rank)
	 * rows touched instead of O(N).
	 *
	 * <p>Win: on a board where the player ranks 100th out of 10k, the
	 * CTE form reads 10000 rows; this form reads ~100. For player
	 * position checks this is the single biggest query optimisation.
	 *
	 * <p>The deltas at the end resolve to {@code t1}'s columns by name
	 * — the outer query's only table alias is {@code t1}, so unqualified
	 * column references are unambiguous to the optimiser.
	 */
	private final String GET_POSITION_FAST = "/*%s*/select t1.'id', t1.'value', t1.'namecache', t1.'prefixcache', t1.'suffixcache', t1.'displaynamecache', "
			+ "(select count(*) + 1 from '%s' t2 where t2.'%s' %s t1.'%s' or (t2.'%s' = t1.'%s' and t2.'namecache' > t1.'namecache')) as position, "
			+ deltaBuilder() + " "
			+ "from '%s' t1 where t1.'id' = ?";
	private final Map<String, String> CREATE_TABLE = ImmutableMap.of(
			"sqlite", "create table if not exists '%s' (id TEXT PRIMARY KEY, value DECIMAL(65, 2)"+columnBuilder("DECIMAL(65, 2)")+", namecache TEXT, prefixcache TEXT, suffixcache TEXT, displaynamecache TEXT)",
			"h2", "create table if not exists '%s' ('id' VARCHAR(36) PRIMARY KEY, 'value' DECIMAL(65, 2)"+columnBuilder("DECIMAL(65, 2)")+", 'namecache' VARCHAR(16), 'prefixcache' VARCHAR(1024), 'suffixcache' VARCHAR(1024), 'displaynamecache' VARCHAR(2048))",
			"mysql", "create table if not exists '%s' ('id' VARCHAR(36) PRIMARY KEY, 'value' DECIMAL(65, 2)"+columnBuilder("DECIMAL(65, 2)")+", 'namecache' VARCHAR(16), 'prefixcache' VARCHAR(1024), 'suffixcache' VARCHAR(1024), 'displaynamecache' VARCHAR(2048))"
	);
	private final String REMOVE_PLAYER = "delete from '%s' where 'namecache'=?";
	private final Map<String, String> LIST_TABLES = ImmutableMap.of(
			"sqlite", "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%';"
	);
	private final String DROP_TABLE = "drop table '%s';";
	private final String INSERT_PLAYER = "insert into '%s' ('id', 'value', 'namecache', 'prefixcache', 'suffixcache', 'displaynamecache'"+tableBuilder()+") values (?, ?, ?, ?, ?, ?"+qBuilder()+")";
	private final String UPDATE_PLAYER = "update '%s' set 'value'=?, 'namecache'=?, 'prefixcache'=?, 'suffixcache'=?, 'displaynamecache'=?"+updateBuilder()+" where id=?";
	private final String INSERT_OR_UPDATE_PLAYER = "insert into '%s' ('id', 'value', 'namecache', 'prefixcache', 'suffixcache', 'displaynamecache'"+tableBuilder()+") values (?, ?, ?, ?, ?, ?"+qBuilder()+") ON DUPLICATE KEY update 'value'=?, 'namecache'=?, 'prefixcache'=?, 'suffixcache'=?, 'displaynamecache'=?"+updateBuilder();
	private final String INSERT_OR_UPDATE_PLAYER_H2 = "merge into '%s' ('id', 'value', 'namecache', 'prefixcache', 'suffixcache', 'displaynamecache'"+tableBuilder()+") values (?, ?, ?, ?, ?, ?"+qBuilder()+")";
	private final String QUERY_LASTTOTAL = "select '%s' from '%s' where id=?";
	private final String QUERY_LASTRESET = "select '%s' from '%s' limit 1";
	private final String QUERY_IDVALUE = "select id,'value' from '%s'";
	private final String UPDATE_RESET = "update '%s' set '%s'=?, '%s'=?, '%s'=? where id=?";
	private final String QUERY_ALL = "select * from '%s'";
	private final String CREATE_TIMESTAMP_INDEX = "create index %s_timestamp on '%s' (%s_timestamp)";
	// Indexes on the columns we ORDER BY when fetching the leaderboard.
	// Without these, every top-10 query was a full table scan + sort — and the Bedrock
	// menu fires up to 10 SELECT_POSITION queries per category-detail open.
	private final String CREATE_VALUE_INDEX = "create index %s_value_idx on '%s' ('value')";
	private final String CREATE_DELTA_INDEX = "create index %s_%s_delta_idx on '%s' (%s_delta)";
	/**
	 * Composite (value, namecache) index — covers the GUI's exact
	 * {@code ORDER BY value DESC, namecache DESC LIMIT N} pattern and lets
	 * the optimiser stream rows from the index without any filesort step
	 * for the namecache tiebreak. Without this, MySQL uses the value
	 * single-col index and then sorts each value-bucket by namecache in
	 * memory — adds latency on boards with many ties.
	 */
	private final String CREATE_VALUE_NAME_INDEX = "create index %s_value_name_idx on '%s' ('value', 'namecache')";
	/** Same composite for each {@code <type>_delta + namecache}. */
	private final String CREATE_DELTA_NAME_INDEX = "create index %s_%s_delta_name_idx on '%s' (%s_delta, 'namecache')";



	public LeaderboardPlugin getPlugin() {
		return plugin;
	}

	ConfigFile storageConfig;
	final LeaderboardPlugin plugin;
	final CacheMethod method;

	final String tablePrefix;

	List<String> nonExistantBoards = new ArrayList<>();

	public Cache(LeaderboardPlugin plugin) {
		this.plugin = plugin;

		if(plugin.getDataFolder().mkdirs()) {
			plugin.getLogger().info("Directory created");
		}

		try {
			storageConfig = new ConfigFile(plugin.getDataFolder(), plugin.getLogger(), "cache_storage.yml");
		} catch (ConfigurateException e) {
			plugin.getLogger().log(Level.SEVERE, "Error when loading cache storage config! The plugin may not work properly!", e);
		}

		String methodStr = storageConfig.getString("method");
		if(methodStr.equalsIgnoreCase("mysql")) {
			plugin.getLogger().info("Using MySQL for board cache. ("+methodStr+")");
			method = new MysqlMethod();
			tablePrefix = storageConfig.getString("table_prefix");
			q = "`";
		} else if(methodStr.equalsIgnoreCase("sqlite")) {
			plugin.getLogger().info("Using SQLite for board cache. ("+methodStr+")");
			method = new SqliteMethod();
			tablePrefix = "";
			q = "'";
		} else {
			plugin.getLogger().info("Using H2 flatfile for board cache. ("+methodStr+")");
			method = new H2Method();
			tablePrefix = "";
			q = "`";
		}
		method.init(plugin, storageConfig, this);
	}

	/**
	 * Get a stat. It is recommended you use TopManager#getStat instead of this,
	 * unless it is of absolute importance that you have the most up-to-date information
	 * @param position The position to get
	 * @param board The board
	 * @return The StatEntry representing the position of the board
	 */
	public StatEntry getStat(int position, String board, TimedType type) {
		if(!plugin.getTopManager().boardExists(board)) {
			if(!nonExistantBoards.contains(board)) {
				nonExistantBoards.add(board);
			}
			return StatEntry.boardNotFound(position, board, type);
		}
		boolean reverse = plugin.getAConfig().getStringList("reverse-sort").contains(board);
		try {
			String sortBy = type == TimedType.ALLTIME ? "value" : type.lowerName() + "_delta";
			try (Connection conn = method.getConnection();
				 PreparedStatement ps = conn.prepareStatement(String.format(
					method.formatStatement(SELECT_POSITION),
					tablePrefix+board,
					sortBy,
					reverse ? "asc" : "desc",
					position-1
			))) {
				ResultSet r = ps.executeQuery();
				StatEntry se = processData(r, sortBy, position, board, type);
				return se;
			}
		} catch(SQLException e) {
			plugin.getLogger().log(Level.WARNING, "Unable to get stat of player:", e);
			return StatEntry.error(position, board, type);
		}
	}

	public List<Integer> rolling = new CopyOnWriteArrayList<>();

	/**
	 * Batched top-N fetch. Returns positions {@code 1..n} as a list in a
	 * SINGLE database round-trip — replaces {@code n} separate
	 * {@link #getStat(int, String, TimedType)} calls each doing
	 * {@code LIMIT 1 OFFSET k}.
	 *
	 * <p>The B-tree index on {@code (sortcol, namecache)} (created by
	 * {@link #createBoard} / {@link #ensureIndexesOnExistingBoards})
	 * already orders rows the way we want, so the planner streams the
	 * first {@code n} index entries straight into the result — no
	 * filesort, no per-call setup overhead.
	 *
	 * <p>Used by the Redis writer refresh and the in-memory snapshot
	 * refresh, both of which previously fired 10 sequential
	 * {@code LIMIT 1 OFFSET k} queries per (board × timed-type) cell.
	 * Now 1 query per cell — same data, 10× fewer round-trips.
	 *
	 * <p>The returned list size is {@code <= n}; missing positions are
	 * dropped (board has fewer than n players). Each entry has its
	 * {@code position} set to its 1-based rank.
	 */
	public List<StatEntry> getTopN(String board, TimedType type, int n) {
		if (n <= 0) return java.util.Collections.emptyList();
		if (!plugin.getTopManager().boardExists(board)) {
			if (!nonExistantBoards.contains(board)) nonExistantBoards.add(board);
			return java.util.Collections.emptyList();
		}
		boolean reverse = plugin.getAConfig().getStringList("reverse-sort").contains(board);
		String sortBy = type == TimedType.ALLTIME ? "value" : type.lowerName() + "_delta";
		List<StatEntry> out = new java.util.ArrayList<>(n);
		try (Connection conn = method.getConnection();
			 PreparedStatement ps = conn.prepareStatement(String.format(
					method.formatStatement(SELECT_TOP_N),
					tablePrefix + board,
					sortBy,
					reverse ? "asc" : "desc",
					n
			))) {
			try (ResultSet rs = ps.executeQuery()) {
				int position = 0;
				while (rs.next() && position < n) {
					position++;
					StatEntry se = processRow(rs, sortBy, position, board, type);
					if (se != null) out.add(se);
				}
			}
		} catch (SQLException e) {
			plugin.getLogger().log(Level.WARNING, "Unable to fetch top-N for " + board + "/" + type.lowerName(), e);
		}
		return out;
	}

	/**
	 * Variant of {@link #processData} that does NOT close the ResultSet
	 * (since the caller is iterating multiple rows) and does NOT call
	 * {@code rs.next()} itself (the caller controls iteration). Returns
	 * null if the row is unreadable.
	 */
	private StatEntry processRow(ResultSet r, String sortBy, int position, String board, TimedType type) {
		try {
			String uuidRaw = r.getString(1);
			String name = r.getString(3);
			String prefix = r.getString(4);
			String suffix = r.getString(5);
			String displayName = r.getString(6);
			double value = r.getDouble(dataSortByIndexes.computeIfAbsent(sortBy, k -> {
				try { return r.findColumn(sortBy); }
				catch (SQLException ex) { return -1; }
			}));
			if (uuidRaw == null) return null;
			if (name == null) name = "-Unknown-";
			if (prefix == null) prefix = "";
			if (suffix == null) suffix = "";
			if (displayName == null) displayName = name;
			return new StatEntry(position, board, prefix, name, displayName,
					java.util.UUID.fromString(uuidRaw), suffix, value, type);
		} catch (SQLException e) {
			plugin.getLogger().log(Level.WARNING, "Row read failed for " + board + "/" + type.lowerName(), e);
			return null;
		}
	}

	private final Map<String, Integer> sortByIndexes = new ConcurrentHashMap<>();
	public StatEntry getStatEntry(OfflinePlayer player, String board, TimedType type) {
		long start = System.currentTimeMillis();
		if(!plugin.getTopManager().boardExists(board)) {
			if(!nonExistantBoards.contains(board)) {
				nonExistantBoards.add(board);
			}
			return StatEntry.boardNotFound(-3, board, type);
		}
		boolean reverse = plugin.getAConfig().getStringList("reverse-sort").contains(board);
		StatEntry r = null;
		try {
			String sortBy = type == TimedType.ALLTIME ? "value" : type.lowerName() + "_delta";
			// GET_POSITION_FAST instead of the CTE form — same result shape
			// (col 7 = position, value resolved via findColumn(sortBy)) but
			// only scans O(rank) rows via the composite (sortcol, namecache)
			// index, instead of materialising ROW_NUMBER for every row.
			// Reverse-sort boards flip the comparison operator from > to <
			// to invert the ordering semantics.
			try (Connection conn = method.getConnection();
				 PreparedStatement ps = conn.prepareStatement(String.format(
					method.formatStatement(GET_POSITION_FAST),
					board,                      // 1: SQL comment marker
					tablePrefix+board,          // 2: subquery FROM
					sortBy,                     // 3: t2.<sortcol> (compare)
					reverse ? "<" : ">",        // 4: comparison operator
					sortBy,                     // 5: t1.<sortcol>
					sortBy,                     // 6: t2.<sortcol> for tie
					sortBy,                     // 7: t1.<sortcol> for tie
					tablePrefix+board           // 8: outer FROM
			))) {
				ps.setString(1, player.getUniqueId().toString());

				try (ResultSet rs = ps.executeQuery()) {
					rs.next();

					String uuidraw = null;
					double value = -1;
					String name = "-Unknown-";
					String displayName = name;
					String prefix = "";
					String suffix = "";
					int position = -1;
					try {
						uuidraw = rs.getString(1);
						name = rs.getString(3);
						prefix = rs.getString(4);
						suffix = rs.getString(5);
						displayName = rs.getString(6);
						position = rs.getInt(7);
						value = rs.getDouble(sortByIndexes.computeIfAbsent(sortBy,
								k -> {
									try {
										Debug.info("Calculating (statentry) column for "+sortBy);
										return rs.findColumn(sortBy);
									} catch (SQLException e) {
										plugin.getLogger().log(Level.SEVERE, "Error while finding a column for "+sortBy, e);
										return -1;
									}
								}
						));
					} catch(SQLException e) {
						if(
								!e.getMessage().contains("ResultSet closed") &&
										!e.getMessage().contains("empty result set") &&
										!e.getMessage().contains("[2000-")
						) {
							throw e;
						}
					}
					if(prefix == null) prefix = "";
					if(suffix == null) suffix = "";
					if(displayName == null) displayName = name;
					if(uuidraw != null) {
						r = new StatEntry(position, board, prefix, name, displayName, UUID.fromString(uuidraw), suffix, value, type);
					}
				}
			}
		} catch (SQLException e) {
			plugin.getLogger().log(Level.WARNING, "Unable to get position/value of player:", e);
			return StatEntry.error(-1, board, type);
		}
		rolling.add((int) (System.currentTimeMillis()-start));
		if(rolling.size() > 50) {
			rolling.remove(0);
		}
		if(r == null) {
			return StatEntry.noData(plugin, -1, board, type);
		}
		return r;
	}

	public int getBoardSize(String board) {
		if(!plugin.getTopManager().boardExists(board)) {
			if(!nonExistantBoards.contains(board)) {
				nonExistantBoards.add(board);
			}
			return -3;
		}

		int size;

		try (Connection connection = method.getConnection();
			 PreparedStatement ps = connection.prepareStatement(String.format(
					method.formatStatement("select COUNT(1) from '%s'"),
					tablePrefix+board
			 ));
			 ResultSet rs = ps.executeQuery()) {

			rs.next();
			size = rs.getInt(1);

		} catch (SQLException e) {
			if(
					!e.getMessage().contains("ResultSet closed") &&
							!e.getMessage().contains("empty result set") &&
							!e.getMessage().contains("[2000-")
			) {
				plugin.getLogger().log(Level.WARNING, "Unable to get size of board:", e);
				return -1;
			} else {
				return 0;
			}
		}

		return size;
	}

	public double getTotal(String board, TimedType type) {
		if(!plugin.getTopManager().boardExists(board)) {
			if(!nonExistantBoards.contains(board)) {
				nonExistantBoards.add(board);
			}
			return -3;
		}

		double total;

		try (Connection connection = method.getConnection();
			 PreparedStatement ps = connection.prepareStatement(String.format(
					method.formatStatement("select SUM(%s) as total from '%s'"),
					type == TimedType.ALLTIME ? "\"value\"" : type.lowerName() + "_delta",
					tablePrefix+board
			 ));
			 ResultSet rs = ps.executeQuery()) {

			rs.next();
			total = rs.getDouble(1);

		} catch (SQLException e) {
			if(
					!e.getMessage().contains("ResultSet closed") &&
							!e.getMessage().contains("empty result set") &&
							!e.getMessage().contains("[2000-")
			) {
				plugin.getLogger().log(Level.WARNING, "Unable to get total of board:", e);
				return -1;
			} else {
				return 0;
			}
		}

		return total;
	}

	public boolean createBoard(String name) {
		try {
			try (Connection conn = method.getConnection()) {
				try (PreparedStatement ps = conn.prepareStatement(method.formatStatement(String.format(
						CREATE_TABLE.get(method.getName()),
						tablePrefix+name
				)))) {
					ps.executeUpdate();
				}

				for (TimedType type : TimedType.values()) {
					if(type == TimedType.ALLTIME) continue;
					try (PreparedStatement ps = conn.prepareStatement(method.formatStatement(String.format(
							CREATE_TIMESTAMP_INDEX,
							type.lowerName(),
							tablePrefix+name,
							type.lowerName()
					)))) {
						ps.executeUpdate();
					} catch(SQLException e) {
						if(!e.getMessage().contains("already exists") && !e.getMessage().contains("Duplicate key") ) throw e;
					}
				}

				// Value (ALLTIME) ordering index — speeds up "order by value desc limit 1 offset N"
				try (PreparedStatement ps = conn.prepareStatement(method.formatStatement(String.format(
						CREATE_VALUE_INDEX,
						tablePrefix+name,
						tablePrefix+name
				)))) {
					ps.executeUpdate();
				} catch(SQLException e) {
					if(!e.getMessage().contains("already exists") && !e.getMessage().contains("Duplicate key")) throw e;
				}

				// Per-timed-type delta ordering indexes — daily/weekly/monthly/yearly
				for (TimedType type : TimedType.values()) {
					if(type == TimedType.ALLTIME) continue;
					try (PreparedStatement ps = conn.prepareStatement(method.formatStatement(String.format(
							CREATE_DELTA_INDEX,
							tablePrefix+name,
							type.lowerName(),
							tablePrefix+name,
							type.lowerName()
					)))) {
						ps.executeUpdate();
					} catch(SQLException e) {
						if(!e.getMessage().contains("already exists") && !e.getMessage().contains("Duplicate key")) throw e;
					}
				}

				// Composite (value, namecache) index — covers the full
				// ORDER BY clause so the namecache tiebreak doesn't trigger
				// a filesort. The single-col value index above stays for
				// equality lookups; this one wins for ORDER BY + LIMIT.
				try (PreparedStatement ps = conn.prepareStatement(method.formatStatement(String.format(
						CREATE_VALUE_NAME_INDEX,
						tablePrefix+name,
						tablePrefix+name
				)))) {
					ps.executeUpdate();
				} catch(SQLException e) {
					if(!e.getMessage().contains("already exists") && !e.getMessage().contains("Duplicate key")) throw e;
				}

				// Composite (<type>_delta, namecache) — same idea per timed type.
				for (TimedType type : TimedType.values()) {
					if(type == TimedType.ALLTIME) continue;
					try (PreparedStatement ps = conn.prepareStatement(method.formatStatement(String.format(
							CREATE_DELTA_NAME_INDEX,
							tablePrefix+name,
							type.lowerName(),
							tablePrefix+name,
							type.lowerName()
					)))) {
						ps.executeUpdate();
					} catch(SQLException e) {
						if(!e.getMessage().contains("already exists") && !e.getMessage().contains("Duplicate key")) throw e;
					}
				}
			}
			plugin.getTopManager().fetchBoards();
			plugin.getContextLoader().calculatePotentialContexts();
			nonExistantBoards.remove(name);
			if(!plugin.getTopManager().boardExists(name)) {
				plugin.getLogger().warning("Failed to create board: It wasnt created, but there was no error!");
				return false;
			}
			return true;
		} catch (SQLException e) {
			plugin.getLogger().log(Level.WARNING, "Unable to create board:", e);
			if(e.getCause() != null) {
				plugin.getLogger().log(Level.WARNING, "Cause:", e);
			}
			return false;
		}
	}

	/**
	 * Ensure ordering indexes exist on every already-created board. Boards
	 * created before {@link #createBoard} grew its index-creation block won't
	 * have the value / delta indexes, which means every top-N query falls
	 * back to a full table scan + sort — exactly the cause of the slow
	 * {@code /leaderboard} GUI open.
	 *
	 * <p>Safe to run repeatedly: each {@code CREATE INDEX} is wrapped in a
	 * try/catch that swallows the "already exists" / duplicate-key error,
	 * matching the same pattern used inside {@link #createBoard}. Runs once
	 * on plugin enable.
	 *
	 * @return the number of new indexes that landed (excludes the "already
	 *         exists" no-ops). 0 means everything was already indexed.
	 */
	public int ensureIndexesOnExistingBoards() {
		int created = 0;
		List<String> boards = getBoards();
		for (String name : boards) {
			try (Connection conn = method.getConnection()) {
				// Timestamp indexes per timed type (daily/weekly/monthly/yearly)
				for (TimedType type : TimedType.values()) {
					if (type == TimedType.ALLTIME) continue;
					try (PreparedStatement ps = conn.prepareStatement(method.formatStatement(String.format(
							CREATE_TIMESTAMP_INDEX,
							type.lowerName(),
							tablePrefix + name,
							type.lowerName()
					)))) {
						ps.executeUpdate();
						created++;
					} catch (SQLException e) {
						if (!isAlreadyExistsError(e)) {
							plugin.getLogger().log(Level.WARNING, "Failed adding timestamp index on " + name + "/" + type.lowerName(), e);
						}
					}
				}
				// Value (ALLTIME ordering) index
				try (PreparedStatement ps = conn.prepareStatement(method.formatStatement(String.format(
						CREATE_VALUE_INDEX,
						tablePrefix + name,
						tablePrefix + name
				)))) {
					ps.executeUpdate();
					created++;
				} catch (SQLException e) {
					if (!isAlreadyExistsError(e)) {
						plugin.getLogger().log(Level.WARNING, "Failed adding value index on " + name, e);
					}
				}
				// Delta indexes per timed type
				for (TimedType type : TimedType.values()) {
					if (type == TimedType.ALLTIME) continue;
					try (PreparedStatement ps = conn.prepareStatement(method.formatStatement(String.format(
							CREATE_DELTA_INDEX,
							tablePrefix + name,
							type.lowerName(),
							tablePrefix + name,
							type.lowerName()
					)))) {
						ps.executeUpdate();
						created++;
					} catch (SQLException e) {
						if (!isAlreadyExistsError(e)) {
							plugin.getLogger().log(Level.WARNING, "Failed adding delta index on " + name + "/" + type.lowerName(), e);
						}
					}
				}

				// Composite (value, namecache) — covers ORDER BY value DESC,
				// namecache DESC LIMIT N without filesort.
				try (PreparedStatement ps = conn.prepareStatement(method.formatStatement(String.format(
						CREATE_VALUE_NAME_INDEX,
						tablePrefix + name,
						tablePrefix + name
				)))) {
					ps.executeUpdate();
					created++;
				} catch (SQLException e) {
					if (!isAlreadyExistsError(e)) {
						plugin.getLogger().log(Level.WARNING, "Failed adding (value, namecache) index on " + name, e);
					}
				}
				// Composite (<type>_delta, namecache) per timed type.
				for (TimedType type : TimedType.values()) {
					if (type == TimedType.ALLTIME) continue;
					try (PreparedStatement ps = conn.prepareStatement(method.formatStatement(String.format(
							CREATE_DELTA_NAME_INDEX,
							tablePrefix + name,
							type.lowerName(),
							tablePrefix + name,
							type.lowerName()
					)))) {
						ps.executeUpdate();
						created++;
					} catch (SQLException e) {
						if (!isAlreadyExistsError(e)) {
							plugin.getLogger().log(Level.WARNING, "Failed adding (" + type.lowerName() + "_delta, namecache) index on " + name, e);
						}
					}
				}
			} catch (SQLException connEx) {
				plugin.getLogger().log(Level.WARNING, "Index-ensure: failed to connect for board " + name, connEx);
			}
		}
		plugin.getLogger().info("[Index Audit] Ensured ordering indexes on " + boards.size()
				+ " board" + (boards.size() == 1 ? "" : "s") + " (" + created + " new index"
				+ (created == 1 ? "" : "es") + " landed)");
		return created;
	}

	private static boolean isAlreadyExistsError(SQLException e) {
		String msg = e.getMessage();
		if (msg == null) return false;
		String lower = msg.toLowerCase();
		return lower.contains("already exists") || lower.contains("duplicate key");
	}

	public boolean removePlayer(String board, String playerName) {

		try (Connection conn = method.getConnection();
			 PreparedStatement ps = conn.prepareStatement(String.format(
					method.formatStatement(REMOVE_PLAYER),
					tablePrefix+board
			 ))) {

			ps.setString(1, playerName);
			ps.executeUpdate();
			return true;
		} catch (SQLException e) {
			plugin.getLogger().log(Level.WARNING, "Unable to remove player from board:", e);
			return false;
		}
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean boardExists(String board) {
		return getBoards().contains(board);
	}

	public List<String> getBoards() {
		List<String> o = new ArrayList<>();

		for(String table : getDbTableList()) {
			if(table.indexOf(tablePrefix) != 0) continue;
			String name = table.substring(tablePrefix.length());
			if(name.equals("extras")) continue;
			o.add(name);
		}

		return o;
	}

	public List<String> getDbTableList() {
		List<String> b = new ArrayList<>();
		try (Connection conn = method.getConnection();
			 Statement statement = conn.createStatement();
			 ResultSet r = statement.executeQuery(
					method.formatStatement(
							LIST_TABLES.getOrDefault(method.getName(), "show tables;")
					)
			)) {
			while(r.next()) {
				String e = r.getString(1);
				if(e.indexOf(tablePrefix) != 0) continue;
				String name = e.substring(tablePrefix.length());
				if(name.equals("extras")) continue;
				b.add(e);
			}
		} catch(SQLException e) {
			plugin.getLogger().log(Level.WARNING, "Unable to get list of tables:", e);
		}
		return b;
	}

	public boolean removeBoard(String board) {
		if(!plugin.getTopManager().boardExists(board)) {
			plugin.getLogger().warning("Attempted to remove board that doesnt exist!");
			return false;
		}
		try {
			if(method instanceof SqliteMethod) {
				((SqliteMethod) method).newConnection();
			}
			try (Connection conn = method.getConnection();
				 PreparedStatement ps = conn.prepareStatement(String.format(
						method.formatStatement(DROP_TABLE),
						tablePrefix+board
				 ))) {
				ps.executeUpdate();
			}
			plugin.getTopManager().fetchBoards();
			plugin.getContextLoader().calculatePotentialContexts();
			if(plugin.getTopManager().boardExists(board)) {
				plugin.getLogger().warning("Attempted to remove a board, but it didnt get removed!");
				return false;
			}
			return true;
		} catch (SQLException e) {
			plugin.getLogger().log(Level.WARNING, "An error occurred while trying to remove a board:", e);
			return false;
		}
	}

	public void updatePlayerStats(OfflinePlayer player) {
		List<String> updatableBoards = plugin.getAConfig().getStringList("only-update");
		for(String b : plugin.getTopManager().getBoards()) {
			if(!updatableBoards.isEmpty() && !updatableBoards.contains(b)) continue;
			if(plugin.isShuttingDown()) return;
			if(player.isOnline() && player.getPlayer() != null) {
				if(
						plugin.getAConfig().getBoolean("enable-dontupdate-permission") &&
								player.getPlayer().hasPermission("ajleaderboards.dontupdate."+b)
				) continue;
			}
			// If this update isnt async, then dont run the event until it is async
			if(!Bukkit.isPrimaryThread()) {
				UpdatePlayerEvent updatePlayerEvent = new UpdatePlayerEvent(new BoardPlayer(b, player));
				Bukkit.getPluginManager().callEvent(updatePlayerEvent);
				if(updatePlayerEvent.isCancelled()) {
					Debug.info("Update for " + player.getName() + " on " + b + " was canceled by an event!");
					continue;
				}
			}
			updateStat(b, player);
		}

		boolean updateDebug = plugin.getAConfig().getBoolean("update-de-bug");

		for (String extra : plugin.getExtraManager().getExtras()) {
			String value;
			try {
				value = PlaceholderAPI.setPlaceholders(player, "%"+extra+"%");
			} catch(Exception e) {
				plugin.getLogger().log(Level.WARNING, "Placeholder %"+extra+"% threw an error for "+player.getName()+":", e);
				continue;
			}
			if(updateDebug) Debug.info("Got '"+value+"' from extra "+extra+" for "+player.getName());

			if(value.equals("%" + extra + "%")) {
				Debug.info("Extra " + extra + " returned itself! (for " + player.getName() + ") Skipping.");
				continue;
			}

			String cached = plugin.getTopManager().getCachedExtra(player.getUniqueId(), extra);
			if(cached != null && cached.equals(value)) {
				if(updateDebug) Debug.info("Skipping updating extra of "+player.getName()+" for "+extra+" because their cached score is the same as their current score");
				continue;
			}

			Runnable runnable = () -> plugin.getExtraManager().setExtra(player.getUniqueId(), extra, value);
			if(Bukkit.isPrimaryThread()) {
				plugin.getTopManager().submit(runnable);
			} else {
				runnable.run();
			}
		}
	}

	List<BoardPlayer> zeroPlayers = new CopyOnWriteArrayList<>();

	public void updateStat(String board, OfflinePlayer player) {
		if(!plugin.getTopManager().boardExists(board)) {
			return;
		}
		boolean debug = plugin.getAConfig().getBoolean("update-de-bug");
		String outputraw;
		double output;
		if(plugin.isShuttingDown()) return;
		try {
			outputraw = PlaceholderAPI.setPlaceholders(player, "%"+alternatePlaceholders(board)+"%")
					.replaceAll(",", "");
			output = plugin.getPlaceholderFormatter().toDouble(outputraw, board);
		} catch(NumberFormatException e) {
			if(debug) Debug.info("Placeholder %"+board+"% for "+player.getName()+" returned a non-number! Ignoring it. Message: " + e);
			return;
		} catch(Exception e) {
			plugin.getLogger().log(Level.WARNING, "Placeholder %"+board+"% for player "+player.getName()+" threw an error:", e);
			return;
		}
		if(debug) Debug.info("Placeholder "+board+" for "+player.getName()+" returned "+output);

		String displayName = player.getName();
		if(player.isOnline() && player.getPlayer() != null) {
			displayName = player.getPlayer().getDisplayName();
		}

		String prefix;
		String suffix;
		if(plugin.hasVault() && player instanceof Player && plugin.getAConfig().getBoolean("fetch-prefix-suffix-from-vault")) {
			prefix = plugin.getVaultChat().getPlayerPrefix((Player)player);
			suffix = plugin.getVaultChat().getPlayerSuffix((Player)player);
			if(prefix == null) {
				prefix = "";
				plugin.getLogger().warning("Got a null prefix for " + player.getName() + " from " + plugin.getVaultChat().getName());
			}
			if(suffix == null) {
				suffix = "";
				plugin.getLogger().warning("Got a null suffix for " + player.getName() + " from " + plugin.getVaultChat().getName());
			}
		} else {
			suffix = "";
			prefix = "";
		}

		boolean waitedUpdate = Bukkit.isPrimaryThread();

		String finalDisplayName = displayName;
		String finalSuffix = suffix;
		String finalPrefix = prefix;
		Runnable updateTask = () -> {

			BoardPlayer boardPlayer = new BoardPlayer(board, player);

			if(waitedUpdate) {
				UpdatePlayerEvent updatePlayerEvent = new UpdatePlayerEvent(boardPlayer);
				Bukkit.getPluginManager().callEvent(updatePlayerEvent);
				if(updatePlayerEvent.isCancelled()) {
					Debug.info("Update for " + player.getName() + " on " + board + " was canceled by an event!");
					return;
				}
			}

			StatEntry cached = plugin.getTopManager().getCachedStatEntry(player, board, TimedType.ALLTIME, plugin.getAConfig().getBoolean("check-cache-on-update"));
			if(cached != null && cached.hasPlayer() &&
					cached.getScore() == output &&
					cached.getPlayerDisplayName().equals(finalDisplayName) &&
					cached.getPrefix().equals(finalPrefix) &&
					cached.getSuffix().equals(finalSuffix)
			) {
				if(debug) Debug.info("Skipping updating of "+player.getName()+" for "+board+" because their cached score is the same as their current score");
				return;
			}

			if(plugin.getAConfig().getStringList("dont-add-zero").contains(board)) {
				if(output == 0) {
					Debug.info("Skipping " + player.getName() + " because they returned 0 for " + board + "(dont-add-zero)");
					return;
				}
			}

			if(plugin.getAConfig().getBoolean("require-zero-validation")) {
				if(output == 0 && !zeroPlayers.contains(boardPlayer)) {
					zeroPlayers.add(boardPlayer);
					Debug.info("Skipping "+player.getName()+" because they returned 0 for "+board);
					return;
				} else if(output == 0 && zeroPlayers.contains(boardPlayer)) {
					Debug.info("Not skipping "+player.getName()+" because they still returned 0 for "+board);
				} else if(output != 0) {
					zeroPlayers.remove(boardPlayer);
				}
			}

			Map<TimedType, Double> lastTotals = getAllLastTotals(board, player);


			if(debug) Debug.info("Updating "+player.getName()+" on board "+board+" with values v: "+output+" suffix: "+ finalSuffix +" prefix: "+ finalPrefix);
			try(Connection conn = method.getConnection()) {
				PreparedStatement statement = conn.prepareStatement(String.format(
						method.formatStatement(method.getName().equals("h2") ? INSERT_OR_UPDATE_PLAYER_H2 : INSERT_OR_UPDATE_PLAYER),
						tablePrefix+board
				));
				statement.setString(1, player.getUniqueId().toString());
				statement.setDouble(2, output);
				statement.setString(3, player.getName());
				statement.setString(4, finalPrefix);
				statement.setString(5, finalSuffix);
				statement.setString(6, finalDisplayName);

				Map<TimedType, Double> timedTypeValues = new HashMap<>();
				timedTypeValues.put(TimedType.ALLTIME, output);

				int i = 6;
				for(TimedType type : TimedType.values()) {
					if(type == TimedType.ALLTIME) continue;
					long lastReset = plugin.getTopManager().getLastReset(board, type)*1000;
					if(plugin.isShuttingDown()) {
						return;
					}
					Double lastTotal = lastTotals.get(type);
					double lastTotalNumber = lastTotal == null ? output : lastTotal;
					double timedOut = output-lastTotalNumber;
					statement.setDouble(++i, timedOut); // delta
					statement.setDouble(++i, lastTotalNumber); // lasttotal
					statement.setLong(++i, lastReset == 0 ? System.currentTimeMillis() : lastReset); // timestamp
					timedTypeValues.put(type, timedOut);
				}
				if(!method.getName().equals("h2")) {
					statement.setDouble(++i, output);
					statement.setString(++i, player.getName());
					statement.setString(++i, finalPrefix);
					statement.setString(++i, finalSuffix);
					statement.setString(++i, finalDisplayName);

					for(TimedType type : TimedType.values()) {
						if(type == TimedType.ALLTIME) continue;
						Double lastTotal = lastTotals.get(type);
						double lastTotalNumber = lastTotal == null ? output : lastTotal;
						double timedOut = output-lastTotalNumber;
						statement.setDouble(++i, timedOut);
					}
				}

				for (Map.Entry<TimedType, Double> timedTypeDoubleEntry : timedTypeValues.entrySet()) {
					TimedType type = timedTypeDoubleEntry.getKey();
					double timedOut = timedTypeDoubleEntry.getValue();

					StatEntry statEntry = plugin.getTopManager().getCachedStatEntry(player, board, type, false);
					if(statEntry != null && player.getUniqueId().equals(statEntry.getPlayerID())) {
						statEntry.changeScore(timedOut, finalPrefix, finalSuffix);
					}

					Integer position = plugin.getTopManager()
							.positionPlayerCache.getOrDefault(player.getUniqueId(), new HashMap<>())
							.get(new BoardType(board, type));
					Debug.info("Position for " + type + ": " + position);
					if(position != null) {
						StatEntry stat = plugin.getTopManager().getCachedStat(new PositionBoardType(position, board, type), false);
						if(stat != null && player.getUniqueId().equals(stat.getPlayerID())) {
							stat.changeScore(timedOut, finalPrefix, finalSuffix);
						}
					}
				}
				statement.executeUpdate();
				statement.close();

			} catch(SQLException e) {
				if(plugin.isShuttingDown()) return;
				plugin.getLogger().log(Level.WARNING, "Unable to update stat for player:", e);
			}
		};

		if(Bukkit.isPrimaryThread()) {
			plugin.getTopManager().submit(updateTask);
		} else {
			updateTask.run();
		}
	}

	public Double getLastTotal(String board, OfflinePlayer player, TimedType type) {
		Double last = null;
		try (Connection conn = method.getConnection();
			 PreparedStatement ps = conn.prepareStatement(String.format(
					method.formatStatement(QUERY_LASTTOTAL),
					type.lowerName()+"_lasttotal",
					tablePrefix+board
			))) {
			ps.setString(1, player.getUniqueId().toString());
			try (ResultSet rs = ps.executeQuery()) {
				if(method instanceof MysqlMethod || method instanceof H2Method) {
					rs.next();
				}
				last = rs.getDouble(1);
			}
		} catch(SQLException e) {
			String m = e.getMessage();
			if(m.contains("empty result set") || m.contains("ResultSet closed") || m.contains("[2000-")) return last;
			plugin.getLogger().log(Level.WARNING, "Unable to get last total for "+player.getName()+" on "+type+" of "+board, e);
		}
		return last;
	}

	/**
	 * Fetch all last totals for a player on a board in a single query instead of one per TimedType.
	 */
	public Map<TimedType, Double> getAllLastTotals(String board, OfflinePlayer player) {
		Map<TimedType, Double> result = new HashMap<>();
		StringBuilder columns = new StringBuilder();
		List<TimedType> types = new ArrayList<>();
		for (TimedType type : TimedType.values()) {
			if (type == TimedType.ALLTIME) continue;
			types.add(type);
			if (columns.length() > 0) columns.append(",");
			columns.append(q).append(type.lowerName()).append("_lasttotal").append(q);
		}
		String sql = String.format(
				method.formatStatement("select " + columns + " from '%s' where 'id'=?"),
				tablePrefix + board
		);
		try (Connection conn = method.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, player.getUniqueId().toString());
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					for (int i = 0; i < types.size(); i++) {
						double val = rs.getDouble(i + 1);
						result.put(types.get(i), rs.wasNull() ? null : val);
					}
				}
			}
		} catch (SQLException e) {
			String m = e.getMessage();
			if (!m.contains("empty result set") && !m.contains("ResultSet closed") && !m.contains("[2000-")) {
				plugin.getLogger().log(Level.WARNING, "Unable to get last totals for " + player.getName() + " on " + board, e);
			}
		}
		return result;
	}

	public long getLastReset(String board, TimedType type) {
		long last = 0;
		try (Connection conn = method.getConnection();
			 PreparedStatement ps = conn.prepareStatement(String.format(
					method.formatStatement(QUERY_LASTRESET),
					type.lowerName()+"_timestamp",
					tablePrefix+board
			))) {
			try (ResultSet rs = ps.executeQuery()) {
				if(method instanceof MysqlMethod || method instanceof H2Method) {
					rs.next();
				}
				last = rs.getLong(1);
			}
		} catch(SQLException e) {
			String m = e.getMessage();
			if(m.contains("empty result set") || m.contains("ResultSet closed") || m.contains("[2000-")) return last;
			plugin.getLogger().log(Level.WARNING, "Unable to get last reset for "+type+" of "+board, e);
		}
		return last;
	}

	public void reset(String board, TimedType type) throws ExecutionException, InterruptedException {
		if(!plugin.getTopManager().boardExists(board)) return;

		if(!plugin.getAConfig().getBoolean("update-stats")) return;

		List<String> updatableBoards = plugin.getAConfig().getStringList("only-update");
		if(!updatableBoards.isEmpty() && !updatableBoards.contains(board)) return;

		if(type.equals(TimedType.ALLTIME)) {
			throw new IllegalArgumentException("Cannot reset ALLTIME!");
		}

		Bukkit.getPluginManager().callEvent(new PreTimedTypeResetEvent(board, type));

		long startTime = System.currentTimeMillis();
		LocalDateTime startDateTime = LocalDateTime.now();
		long newTime = startDateTime.atOffset(ZoneOffset.UTC).toEpochSecond()*1000;
		Debug.info(board+" "+type+" "+startDateTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME)+" "+newTime);


		List<String> saveableTypes = plugin.getAConfig().getStringList("reset-save-types");
		if(saveableTypes.contains(type.toString()) || saveableTypes.contains("*")) {
			plugin.getResetSaver().save(board, type);
		}


		Debug.info("Resetting "+board+" "+type.lowerName()+" leaderboard");
		long lastReset = plugin.getTopManager().getLastReset(board, type)*1000L;
		if(plugin.isShuttingDown()) {
			return;
		}
		Debug.info("last: "+lastReset+" gap: "+(startTime - lastReset));
		String t = type.lowerName();
		try {
			Map<String, Double> uuids = new HashMap<>();
			try (Connection conn = method.getConnection();
				 PreparedStatement ps = conn.prepareStatement(String.format(
						method.formatStatement(QUERY_IDVALUE),
						tablePrefix+board
				 ));
				 ResultSet rs = ps.executeQuery()) {
				while(rs.next()) {
					uuids.put(rs.getString(1), rs.getDouble(2));
				}
			}
			Partition<String> partition = Partition.ofSize(new ArrayList<>(uuids.keySet()), Math.max(uuids.size()/(int) Math.ceil(method.getMaxConnections()/2D), 1));
			Debug.info("Partition length: "+partition.size()+" uuids size: "+ uuids.size()+" partition chunk size: "+partition.getChunkSize());
			for(List<String> uuidPartition : partition) {
				if(plugin.isShuttingDown()) return;
				try (Connection con = method.getConnection()) {
					for(String idRaw : uuidPartition) {
						if(plugin.isShuttingDown()) return;
						try (PreparedStatement p = con.prepareStatement(String.format(
								method.formatStatement(UPDATE_RESET),
								tablePrefix+board,
								t+"_lasttotal",
								t+"_delta",
								t+"_timestamp"
						))) {
							p.setDouble(1, uuids.get(idRaw));
							p.setDouble(2, 0);
							p.setLong(3, newTime);
							p.setString(4, idRaw);
							p.executeUpdate();
						}
					}
				} catch (SQLException e) {
					plugin.getLogger().log(Level.WARNING, "An error occurred while resetting "+type+" of "+board+":", e);
				}
			}
		} catch (SQLException e) {
			plugin.getLogger().log(Level.WARNING, "An error occurred while resetting "+type+" of "+board+":", e);
		}
		Debug.info("Reset of "+board+" "+type.lowerName()+" took "+(System.currentTimeMillis()-startTime)+"ms");
	}

	public void insertRows(String board, List<DbRow> rows) throws SQLException {
		try (Connection conn = method.getConnection()) {
		for(DbRow row : rows) {
			PreparedStatement statement = conn.prepareStatement(String.format(
					method.formatStatement(INSERT_PLAYER),
					tablePrefix+board
			));
			statement.setString(1, row.getId().toString());
			statement.setDouble(2, row.getValue());
			statement.setString(3, row.getNamecache());
			statement.setString(4, row.getPrefixcache());
			statement.setString(5, row.getSuffixcache());
			statement.setString(6, row.getDisplaynamecache());
			int i = 6;
			for(TimedType type : TimedType.values()) {
				if(type == TimedType.ALLTIME) continue;
				if(plugin.isShuttingDown()) {
					method.close(conn);
				}
				statement.setDouble(++i, row.getDeltas().get(type));
				statement.setDouble(++i, row.getLastTotals().get(type));
				statement.setLong(++i, row.getTimestamps().get(type));
			}

			try {
				statement.executeUpdate();
			} catch(SQLException e) {
				if(e.getMessage().contains("23505") || e.getMessage().contains("Duplicate entry") || e.getMessage().contains("PRIMARY KEY constraint failed")) {
					statement.close();
					continue;
				}
				throw e;
			}
			statement.close();
		}
		} // close try-with-resources connection
	}

	public List<DbRow> getRows(String board) throws SQLException {
		try (Connection conn = method.getConnection();
			 PreparedStatement ps = conn.prepareStatement(String.format(
					method.formatStatement(QUERY_ALL),
					tablePrefix+board
			 ));
			 ResultSet resultSet = ps.executeQuery()) {

			List<DbRow> out = new ArrayList<>();
			while(resultSet.next()) {
				out.add(new DbRow(resultSet));
			}
			return out;
		}
	}

	public CacheMethod getMethod() {
		return method;
	}

	private static final HashMap<String, String> altPlaceholders = new HashMap<String, String>() {{
		put("ajpk_stats_highscore", "ajpk_stats_highscore_nocache");
		put("ajtr_stats_wins", "ajtr_stats_wins_nocache");
		put("ajtr_stats_losses", "ajtr_stats_losses_nocache");
		put("ajtr_stats_gamesplayed", "ajtr_stats_gamesplayer_nocache");
	}};
	public static String alternatePlaceholders(String board) {
		return altPlaceholders.getOrDefault(board, board);
	}

	public String getTablePrefix() {
		return tablePrefix;
	}

	private String deltaBuilder() {
		StringBuilder deltaBuilder = new StringBuilder();
		for(TimedType t : TimedType.values()) {
			if(t == TimedType.ALLTIME) continue;
			deltaBuilder.append(q).append(t.lowerName()).append("_delta").append(q).append(",");
		}
		return deltaBuilder.deleteCharAt(deltaBuilder.length()-1).toString();
	}
	private String columnBuilder(String t) {
		String q = "'";
		StringBuilder columns = new StringBuilder();
		for(TimedType type : TimedType.values()) {
			if(type == TimedType.ALLTIME) continue;
			columns
					.append(",\n").append(q).append(type.lowerName()).append("_delta").append(q).append(" ").append(t)
					.append(",\n").append(q).append(type.lowerName()).append("_lasttotal").append(q).append(" ").append(t)
					.append(",\n").append(q).append(type.lowerName()).append("_timestamp").append(q).append(" ").append(t);
		}
		return columns.toString();
	}
	private String qBuilder() {
		StringBuilder addQs = new StringBuilder();
		for(TimedType type : TimedType.values()) {
			if(type == TimedType.ALLTIME) continue;
			addQs.append(", ?").append(", ?").append(", ?");
		}
		return addQs.toString();
	}

	private String tableBuilder() {
		StringBuilder addTables = new StringBuilder();
		for(TimedType type : TimedType.values()) {
			if(type == TimedType.ALLTIME) continue;
			String name = type.lowerName();
			addTables
					.append(", ").append(name).append("_delta")
					.append(", ").append(name).append("_lasttotal")
					.append(", ").append(name).append("_timestamp");
		}
		return addTables.toString();
	}

	private String updateBuilder() {
		StringBuilder addUpdates = new StringBuilder();
		for(TimedType type : TimedType.values()) {
			if(type == TimedType.ALLTIME) continue;
			String name = type.lowerName();
			addUpdates
					.append(", ").append(name).append("_delta").append("=?");
		}
		return addUpdates.toString();
	}

	Map<String, Integer> dataSortByIndexes = new ConcurrentHashMap<>();
	private StatEntry processData(ResultSet r, String sortBy, int position, String board, TimedType type) throws SQLException {
		String uuidRaw = null;
		double value = -1;
		String name = "-Unknown-";
		String displayName = name;
		String prefix = "";
		String suffix = "";
		if(method instanceof MysqlMethod || method instanceof H2Method) {
			r.next();
		}
		try {
			uuidRaw = r.getString(1);
			name = r.getString(3);
			prefix = r.getString(4);
			suffix = r.getString(5);
			displayName = r.getString(6);
			value = r.getDouble(dataSortByIndexes.computeIfAbsent(sortBy,
					k -> {
						try {
							Debug.info("Calculating (process) column for "+sortBy);
							return r.findColumn(sortBy);
						} catch (SQLException e) {
							plugin.getLogger().log(Level.SEVERE, "Error while finding a column for "+sortBy, e);
							return -1;
						}
					}
			));
		} catch(SQLException e) {
			if(
					!e.getMessage().contains("ResultSet closed") &&
							!e.getMessage().contains("empty result set") &&
							!e.getMessage().contains("[2000-")
			) {
				throw e;
			}
		}
		if(name == null) name = "-Unknown";
		r.close();

		if(prefix == null) prefix = "";
		if(suffix == null) suffix = "";
		if(displayName == null) displayName = name;

		if(uuidRaw == null) {
			return StatEntry.noData(plugin, position, board, type);
		} else {
			return new StatEntry(position, board, prefix, name, displayName, UUID.fromString(uuidRaw), suffix, value, type);
		}
	}

	/**
	 * Cleans a player from some variables to prevent memory leaks.
	 * Should only be called when the player logs out
	 * @param player the player to remove
	 */
	public void cleanPlayer(Player player) {
		zeroPlayers.removeIf(boardPlayer -> boardPlayer.getPlayer().equals(player));
	}

	public List<String> getNonExistantBoards() {
		return nonExistantBoards;
	}
}
