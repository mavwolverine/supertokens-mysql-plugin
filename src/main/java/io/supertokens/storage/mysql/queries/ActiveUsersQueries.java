package io.supertokens.storage.mysql.queries;

import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.storage.mysql.Start;
import io.supertokens.storage.mysql.config.Config;
import io.supertokens.storage.mysql.utils.Utils;
import org.jetbrains.annotations.TestOnly;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.supertokens.storage.mysql.QueryExecutorTemplate.execute;
import static io.supertokens.storage.mysql.QueryExecutorTemplate.update;

public class ActiveUsersQueries {
    static String getQueryToCreateUserLastActiveTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getUserLastActiveTable() + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "user_id VARCHAR(128),"
                + "last_active_time BIGINT UNSIGNED,"
                + "PRIMARY KEY(app_id, user_id),"
                + "FOREIGN KEY (app_id) REFERENCES "
                + Config.getConfig(start).getAppsTable() + "(app_id) ON DELETE CASCADE"
                + " );";
    }

    public static String getQueryToCreateLastActiveTimeIndexForUserLastActiveTable(Start start) {
        return "CREATE INDEX user_last_active_last_active_time_index ON "
                + Config.getConfig(start).getUserLastActiveTable() + "(last_active_time DESC, app_id DESC);";
    }

    public static int countUsersActiveSince(Start start, AppIdentifier appIdentifier, long sinceTime)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT COUNT(*) as total FROM " + Config.getConfig(start).getUserLastActiveTable()
                + " WHERE app_id = ? AND last_active_time >= ?";

        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setLong(2, sinceTime);
        }, result -> {
            if (result.next()) {
                return result.getInt("total");
            }
            return 0;
        });
    }

    public static int countUsersActiveSinceAndHasMoreThanOneLoginMethod(Start start, AppIdentifier appIdentifier,
                                                                        long sinceTime)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT count(1) as c FROM ("
                + "  SELECT count(user_id) as num_login_methods, app_id, primary_or_recipe_user_id"
                + "  FROM " + Config.getConfig(start).getUsersTable()
                + "  WHERE primary_or_recipe_user_id IN ("
                + "    SELECT user_id FROM " + Config.getConfig(start).getUserLastActiveTable()
                + "    WHERE app_id = ? AND last_active_time >= ?"
                + "  )"
                + "  GROUP BY app_id, primary_or_recipe_user_id"
                + ") uc WHERE num_login_methods > 1";
        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setLong(2, sinceTime);
        }, result -> {
            if (result.next()) {
                return result.getInt("c");
            }
            return 0;
        });
    }

    public static int countUsersEnabledTotp(Start start, AppIdentifier appIdentifier)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT COUNT(*) as total FROM " + Config.getConfig(start).getTotpUsersTable()
                + " WHERE app_id = ?";

        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
        }, result -> {
            if (result.next()) {
                return result.getInt("total");
            }
            return 0;
        });
    }

    public static int countUsersEnabledTotpAndActiveSince(Start start, AppIdentifier appIdentifier, long sinceTime)
            throws SQLException, StorageQueryException {
        String QUERY =
                "SELECT COUNT(*) as total FROM " + Config.getConfig(start).getTotpUsersTable() + " AS totp_users "
                        + "INNER JOIN " + Config.getConfig(start).getUserLastActiveTable() + " AS user_last_active "
                        + "ON totp_users.user_id = user_last_active.user_id "
                        + "WHERE user_last_active.app_id = ? AND user_last_active.last_active_time >= ?";

        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setLong(2, sinceTime);
        }, result -> {
            if (result.next()) {
                return result.getInt("total");
            }
            return 0;
        });
    }

    public static int updateUserLastActive(Start start, AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getUserLastActiveTable()
                + "(app_id, user_id, last_active_time) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE last_active_time = ?";

        long now = System.currentTimeMillis();
        return update(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
            pst.setLong(3, now);
            pst.setLong(4, now);
        });
    }

    @TestOnly
    public static int updateUserLastActive(Start start, AppIdentifier appIdentifier, String userId, long timestamp)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getUserLastActiveTable()
                + "(app_id, user_id, last_active_time) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE last_active_time = ?";

        return update(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
            pst.setLong(3, timestamp);
            pst.setLong(4, timestamp);
        });
    }

    public static Long getLastActiveByUserId(Start start, AppIdentifier appIdentifier, String userId)
            throws StorageQueryException {
        String QUERY = "SELECT last_active_time FROM " + Config.getConfig(start).getUserLastActiveTable()
                + " WHERE app_id = ? AND user_id = ?";

        try {
            return execute(start, QUERY, pst -> {
                pst.setString(1, appIdentifier.getAppId());
                pst.setString(2, userId);
            }, res -> {
                if (res.next()) {
                    return res.getLong("last_active_time");
                }
                return null;
            });
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    public static void deleteUserActive_Transaction(Connection con, Start start, AppIdentifier appIdentifier,
                                                    String userId)
            throws StorageQueryException, SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getUserLastActiveTable()
                + " WHERE app_id = ? AND user_id = ?";

        update(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        });
    }

    public static int countUsersThatHaveMoreThanOneLoginMethodOrTOTPEnabledAndActiveSince(Start start,
                                                                                          AppIdentifier appIdentifier,
                                                                                          long sinceTime)
            throws SQLException, StorageQueryException {
        // TODO: Active users are present only on public tenant and MFA users may be present on different storages
        String QUERY =
                "SELECT COUNT(user_id) as c FROM ("
                        + "  " // users with more than one login method
                        + "    SELECT primary_or_recipe_user_id AS user_id FROM ("
                        + "      SELECT COUNT(user_id) as num_login_methods, app_id, primary_or_recipe_user_id"
                        + "      FROM " + Config.getConfig(start).getAppIdToUserIdTable()
                        + "      WHERE app_id = ? AND primary_or_recipe_user_id IN ("
                        + "        SELECT user_id FROM " + Config.getConfig(start).getUserLastActiveTable()
                        + "        WHERE app_id = ? AND last_active_time >= ?"
                        + "      )"
                        + "      GROUP BY app_id, primary_or_recipe_user_id"
                        + "    ) AS nloginmethods"
                        + "    WHERE num_login_methods > 1"
                        + "  UNION" // TOTP users
                        + "    SELECT user_id FROM " + Config.getConfig(start).getTotpUsersTable()
                        + "    WHERE app_id = ? AND user_id IN ("
                        + "      SELECT user_id FROM " + Config.getConfig(start).getUserLastActiveTable()
                        + "      WHERE app_id = ? AND last_active_time >= ?"
                        + "    )"
                        + "  "
                        + ") AS all_users";

        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, appIdentifier.getAppId());
            pst.setLong(3, sinceTime);
            pst.setString(4, appIdentifier.getAppId());
            pst.setString(5, appIdentifier.getAppId());
            pst.setLong(6, sinceTime);
        }, result -> {
            return result.next() ? result.getInt("c") : 0;
        });
    }

    public static Map<String, Long> getLastActiveByMultipleUserIds(Start start, AppIdentifier appIdentifier, List<String> userIds)
            throws StorageQueryException {
        String QUERY = "SELECT user_id, last_active_time FROM " + Config.getConfig(start).getUserLastActiveTable()
                + " WHERE app_id = ? AND user_id IN ( " + Utils.generateCommaSeperatedQuestionMarks(userIds.size())+ " )";

        try {
            return execute(start, QUERY, pst -> {
                pst.setString(1, appIdentifier.getAppId());
                for (int i = 0; i < userIds.size(); i++) {
                    pst.setString(2+i, userIds.get(i));
                }
            }, res -> {
                Map<String, Long> lastActiveByUserIds = new HashMap<>();
                if (res.next()) {
                    String userId = res.getString("user_id");
                    lastActiveByUserIds.put(userId, res.getLong("last_active_time"));
                }
                return lastActiveByUserIds;
            });
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }
}
