/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.verizon.mms.util;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.database.sqlite.SQLiteProgram;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.os.SystemClock;

import com.strumsoft.android.commons.logger.Logger;

public final class SqliteWrapper {
    private static HashSet<String> queryMap;
    private static Field bindArgs;
    private static Pattern updateOrDeletePat;
    private static Pattern spacePat;
    private static Pattern wherePat;
    private static Pattern unequalPat;
    private static Pattern equalPat;
    private static final String[] countCol = { "COUNT(*) AS count" };
    private static final String SQLITE_EXCEPTION_DETAIL_MESSAGE = "unable to open database file";

    static {
        if (Logger.IS_DEBUG_ENABLED) {
            queryMap = new HashSet<String>();
            updateOrDeletePat = Pattern.compile("^\\s*(?:UPDATE|DELETE FROM)\\s+([^\\s]+)\\s+(?:WHERE\\s+(.+))?", CASE_INSENSITIVE);
            spacePat = Pattern.compile("\\s+");
            wherePat = Pattern.compile("^(.* where )(.*)$", CASE_INSENSITIVE);
            unequalPat = Pattern.compile("!=\\s*[-'_\\.\\p{Alnum}]+| not in\\s*\\([^)]+\\)\\s*", CASE_INSENSITIVE);
            equalPat = Pattern.compile("\\s*(=|==|>\\s*=|<\\s*=|>|<)\\s*[-'_\\.\\p{Alnum}]+| in\\s*\\([-'_\\.,\\s\\p{Alnum}]+\\)", CASE_INSENSITIVE);

            try {
                bindArgs = SQLiteProgram.class.getDeclaredField("mBindArgs");
                bindArgs.setAccessible(true);
            } catch (Throwable t) {
                Logger.debug(t);
            }
        }
    }

    private SqliteWrapper() {
        // Forbidden being instantiated.
    }

    // FIXME: need to optimize this method.
    public static boolean isLowMemory(SQLiteException e) {
        return e instanceof SQLiteDiskIOException
				|| e instanceof SQLiteFullException || e.getMessage().equals(SQLITE_EXCEPTION_DETAIL_MESSAGE);
    }

    public static void checkSQLiteException(Context context, SQLiteException e) {
		if (isLowMemory(e)) {
			if (Logger.IS_ERROR_ENABLED) {
				Logger.error("handle OOM error and stop the service", e);
			}
		} else {
			throw e;
		}
    }

    public static Cursor query(Context context, ContentResolver resolver, Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder) {
        final long start;
        final String params;
        if (Logger.IS_DEBUG_ENABLED) {
            params = "uri = " + uri + ", cols = " + Arrays.toString(projection) + ", selection = <"
                    + selection + ">, args = " + truncate(selectionArgs) + ", sort = " + sortOrder;
            Logger.debug(SqliteWrapper.class, "query: " + params);
	        start = SystemClock.uptimeMillis();
        }

        try {
            final Cursor cursor = resolver != null ? resolver.query(uri, projection, selection,
                    selectionArgs, sortOrder) : null;

            if (Logger.IS_DEBUG_ENABLED) {
                final long queryTime = SystemClock.uptimeMillis() - start;
                final String count = cursor == null ? "null" : Integer.toString(cursor.getCount());
                final long fillTime = SystemClock.uptimeMillis() - (start + queryTime);
                Logger.debug(SqliteWrapper.class, "query: query time = " + queryTime + "ms, fill time = " + fillTime
                        + "ms, " + params + ", returning " + count);
            }

            return cursor;
        } catch (SQLiteException e) {
            if (Logger.IS_ERROR_ENABLED) {
                Logger.error(true, SqliteWrapper.class, "query exception:", e);
            }
            checkSQLiteException(context, e);
            throw e;
        }
    }

    public static Cursor queryOrThrow(Context context, ContentResolver resolver, Uri uri,
            String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        final long start;
        final String params;
        if (Logger.IS_DEBUG_ENABLED) {
            params = "uri = " + uri + ", cols = " + Arrays.toString(projection) + ", selection = <"
                    + selection + ">, args = " + truncate(selectionArgs) + ", sort = " + sortOrder;
            Logger.debug(SqliteWrapper.class, "queryOrThrow: " + params);
	        start = SystemClock.uptimeMillis();
        }

        final Cursor cursor = resolver.query(uri, projection, selection, selectionArgs, sortOrder);

        if (Logger.IS_DEBUG_ENABLED) {
            final long queryTime = SystemClock.uptimeMillis() - start;
            final String count = cursor == null ? "null" : Integer.toString(cursor.getCount());
            final long fillTime = SystemClock.uptimeMillis() - (start + queryTime);
            Logger.debug(SqliteWrapper.class, "queryOrThrow: query time = " + queryTime + "ms, fill time = "
                    + fillTime + "ms, " + params + ", returning " + count);
        }

        return cursor;
    }

    public static int update(Context context, ContentResolver resolver, Uri uri, ContentValues values,
            String where, String[] selectionArgs) {
        final long start;
        if (Logger.IS_DEBUG_ENABLED) {
            Logger.debug(SqliteWrapper.class, "update: uri = " + uri + ", where = <" + where + ">, args = "
                    + truncate(selectionArgs) + ", values = " + values);
	        start = SystemClock.uptimeMillis();
        }

        try {
            final int rows = resolver.update(uri, values, where, selectionArgs);

            if (Logger.IS_DEBUG_ENABLED) {
                Logger.debug(SqliteWrapper.class, "update: time = " + (SystemClock.uptimeMillis() - start)
                        + "ms, uri = " + uri + ", where = <" + where + ">, args = "
                        + truncate(selectionArgs) + ", values = " + values + ", returning " + rows);
            }

            return rows;

        } catch (SQLiteException e) {
            Logger.error(SqliteWrapper.class, "update exception:", e);
            checkSQLiteException(context, e);
            return -1;
        }
    }

    public static int delete(Context context, ContentResolver resolver, Uri uri, String where, String[] selectionArgs) {
        final long start;
        if (Logger.IS_DEBUG_ENABLED) {
            Logger.debug(SqliteWrapper.class, "delete: uri = " + uri + ", where = <" + where + ">, args = "
                    + truncate(selectionArgs));
	        start = SystemClock.uptimeMillis();
        }

        try {
            final int rows = resolver.delete(uri, where, selectionArgs);

            if (Logger.IS_DEBUG_ENABLED) {
                Logger.debug(SqliteWrapper.class, "delete: time = " + (SystemClock.uptimeMillis() - start)
                        + "ms, uri = " + uri + ", where = <" + where + ">, args = "
                        + truncate(selectionArgs) + ", returning " + rows);
            }

            return rows;

        } catch (SQLiteException e) {
            Logger.error(SqliteWrapper.class, "delete exception:", e);
            checkSQLiteException(context, e);
            return -1;
        }
    }

    public static Uri insert(Context context, ContentResolver resolver, Uri uri, ContentValues values) {
        final long start;
        if (Logger.IS_DEBUG_ENABLED) {
            Logger.debug(SqliteWrapper.class, "insert: uri = " + uri + ", values = " + values);
	        start = SystemClock.uptimeMillis();
        }

        try {
            final Uri ret = resolver.insert(uri, values);

            if (Logger.IS_DEBUG_ENABLED) {
                Logger.debug(SqliteWrapper.class, "insert: time = " + (SystemClock.uptimeMillis() - start)
                        + "ms, uri = " + uri + ", values = " + values + ", returning " + ret);
            }

            return ret;

        } catch (SQLiteException e) {
            Logger.error(SqliteWrapper.class, "insert exception:", e);
            checkSQLiteException(context, e);
            return null;
        }
    }

    public static Uri insert(Context context, Uri uri, ContentValues values) {
        return insert(context, context.getContentResolver(), uri, values);
    }

    public static int delete(Context context, Uri uri, String where, String[] selectionArgs) {
        return delete(context, context.getContentResolver(), uri, where, selectionArgs);
    }

    public static int update(Context context, Uri uri, ContentValues values, String where,
            String[] selectionArgs) {
        return update(context, context.getContentResolver(), uri, values, where, selectionArgs);
    }

    public static Cursor query(Context context, Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        return query(context, context.getContentResolver(), uri, projection, selection, selectionArgs,
            sortOrder);
    }

    /**
     * This Method
     *
     * @param authority
     * @param ops
     * @return
     * @throws OperationApplicationException
     * @throws RemoteException
     */
    public static ContentProviderResult[] applyBatch(Context context, String authority,
            ArrayList<ContentProviderOperation> ops) throws RemoteException, OperationApplicationException {
        return applyBatch(context, context.getContentResolver(), authority, ops);
    }

    public static ContentProviderResult[] applyBatch(Context context, ContentResolver resolver,
            String authority, ArrayList<ContentProviderOperation> ops) throws RemoteException,
            OperationApplicationException {
        try {
            long start;
            if (Logger.IS_DEBUG_ENABLED) {
                Logger.debug(SqliteWrapper.class, "applyBatch: uri = " + authority + ", ops = " + ops);
	            start = SystemClock.uptimeMillis();
            }
            ContentProviderResult[] result = resolver.applyBatch(authority, ops);

            if (Logger.IS_DEBUG_ENABLED) {
                Logger.debug(SqliteWrapper.class, "applyBatch: time = " + (SystemClock.uptimeMillis() - start)
                        + "ms, uri = " + authority + ", ops = " + ops + ", returning " + (result!=null?result.length:0));
            }
            return result;
        } catch (SQLiteException e) {
            checkSQLiteException(context, e);
            return null;
        }
    }

    /**
     * This Method is used to get the row the count.
     *
     * @param context
     * @param uri
     * @param selection
     * @param selectionArgs
     * @return {@link Integer}
     */
    public static int getCount(Context context, Uri uri, String selection, String[] selectionArgs) {
        final Long count = getLong(context, uri, countCol, selection, selectionArgs);
        final int ret = count == null ? 0 : count.intValue();
        if (Logger.IS_DEBUG_ENABLED) {
            Logger.debug(SqliteWrapper.class, "getCount: uri = " + uri + ", selection = <" + selection +
            	">, args = " + truncate(selectionArgs) + ", count = " + ret);
        }
        return ret;
    }

    /**
     * Returns the long value of the first column returned by the query, or null if none.
     */
	public static Long getLong(Context context, Uri uri, String[] col, String where, String[] whereArgs) {
		Long val = null;
		Cursor cursor = null;
		try {
			cursor = query(context, context.getContentResolver(), uri, col, where, whereArgs, null);
			if (cursor != null && cursor.moveToFirst()) {
				val = Long.valueOf(cursor.getLong(0));
			}
		}
		finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return val;
	}

	public static int getCount(SQLiteDatabase db, String table, String where, String[] whereArgs) {
		final Long count = getLong(db, table, countCol, where, whereArgs);
		final int ret = count == null ? 0 : count.intValue();
		if (Logger.IS_DEBUG_ENABLED) {
			Logger.debug(SqliteWrapper.class, "getCount: table = " + table + ", where = <" + where +
				">, args = " + truncate(whereArgs) + ", count = " + ret);
		}
		return ret;
	}

	public static Long getLong(SQLiteDatabase db, String table, String[] cols, String where, String[] whereArgs) {
		Long val = null;
		Cursor cursor = null;
		try {
			cursor = query(db, table, cols, where, whereArgs, null, null, null);
			if (cursor != null && cursor.moveToFirst()) {
				val = Long.valueOf(cursor.getLong(0));
			}
		}
		finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return val;
	}

    public static Long getLong(SQLiteDatabase db, String query, String[] args) {
        Long val = null;
        Cursor cursor = null;
	    try {
	        cursor = rawQuery(db, query, args);
            if (cursor != null && cursor.moveToFirst()) {
                val = Long.valueOf(cursor.getLong(0));
            }
        }
        finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return val;
    }

    /**
     * Returns the string value of the first column returned by the query, or null if none.
     */
	public static String getString(Context context, Uri uri, String col, String where, String[] whereArgs) {
		String val = null;
		Cursor cursor = null;
		try {
			cursor = query(context, context.getContentResolver(), uri, new String[] { col }, where, whereArgs, null);
			if (cursor != null && cursor.moveToFirst()) {
				val = cursor.getString(0);
			}
		}
		finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return val;
	}

    /**
     * Returns the string value of the first column returned by the query, or null if none.
     */
    public static String getString(SQLiteDatabase db, String table, String col, String where, String[] whereArgs) {
        String val = null;
        Cursor cursor = null;
	    try {
	        cursor = query(db, table, new String[] { col }, where, whereArgs, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                val = cursor.getString(0);
            }
        }
        finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return val;
    }

    /**
     * Returns the string value of the first column returned by the query, or null if none.
     */
    public static String getString(SQLiteDatabase db, String query, String[] args) {
        String val = null;
        Cursor cursor = null;
        try {
	        cursor = rawQuery(db, query, args);
            if (cursor != null && cursor.moveToFirst()) {
                val = cursor.getString(0);
            }
        }
        finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return val;
    }

	/**
	 * Returns the string value of the given column for the given table and rowId.
	 */
	public static String getString(SQLiteDatabase db, String table, String queryCol, String rowIdCol, long rowId) {
		final String where = rowIdCol + " = ?";
		final String[] args = { Long.toString(rowId) };
		return getString(db, table, queryCol, where, args);
	}

	public static Cursor query(SQLiteDatabase db, String table, String[] cols, String where, String[] whereArgs,
			String groupBy, String having, String sort) {
		final long start;
		final String params;
		if (Logger.IS_DEBUG_ENABLED) {
			params = "table = " + table + ", cols = " + Arrays.toString(cols) + ", where = <" + where + ">, args = "
				+ truncate(whereArgs) + ", groupBy = " + groupBy + ", having = " + having + ", sort = " + sort;
			Logger.debug(SqliteWrapper.class, "query: " + params);
			start = SystemClock.uptimeMillis();
		}

		final Cursor cursor = db.query(table, cols, where, whereArgs, groupBy, having, sort);

		if (Logger.IS_DEBUG_ENABLED) {
			final long queryTime = SystemClock.uptimeMillis() - start;
			final String count = cursor == null ? "null" : Integer.toString(cursor.getCount());
			final long fillTime = SystemClock.uptimeMillis() - (start + queryTime);
			Logger.debug(SqliteWrapper.class, "query: query time = " + queryTime + "ms, fill time = " + fillTime + "ms, "
				+ params + ", returning " + count);
		}

		return cursor;
	}

	/**
     * This Method
     *
     * @param selection
     * @param maxId
     * @return
     */
    public static long getMax(Context context, Uri uri, String columnName, String selection) {
        long maxId = 0;
        if (columnName == null) {
            throw new NullPointerException("columnName should not be null.columnName=" + columnName);
        }
	    if (Logger.IS_DEBUG_ENABLED) {
	    }
	    String[] projection = new String[] { "MAX(" + columnName + ")" };
        Cursor c = SqliteWrapper.query(context, uri, projection, selection, null, null);
        if (c != null) {
            if (c.moveToFirst()) {
                maxId = c.getLong(0);
            }
            c.close();
        }
        return maxId;
    }

    /**
     * This Method
     *
     * @param selection
     * @param maxId
     * @return
     */
    public static long getMin(Context context, Uri uri, String columnName, String selection) {
        long maxId = 0;
        if (columnName == null) {
            throw new NullPointerException("columnName should not be null.columnName=" + columnName);
        }
	    if (Logger.IS_DEBUG_ENABLED) {
	    }
	    String[] projection = new String[] { "MIN(" + columnName + ")" };
        Cursor c = SqliteWrapper.query(context, uri, projection, selection, null, null);
        if (c != null) {
            if (c.moveToFirst()) {
                maxId = c.getLong(0);
            }
            c.close();
        }
        return maxId;
    }

    public static void dump(Context context, Uri uri, String sort) {
        Cursor c = SqliteWrapper.query(context, uri, null, null, null, sort);
        if (c != null) {
            Logger.debug("*********************************************");
            Logger.debug("URI =" + uri + ",Item count=" + c.getCount());
            Logger.debug("*********************************************");
            Logger.debug("*********************************************");
            String[] columnNames = c.getColumnNames();
            while (c.moveToNext()) {
                StringBuilder builder = new StringBuilder();
                builder.append("[ ");
                for (String cname : columnNames) {
                    builder.append(cname + "=" + c.getString(c.getColumnIndex(cname)) + ",");
                }
                builder.append(" ]");
                Logger.debug(builder.toString());
            }
            Logger.debug("*********************************************");
            c.close();
        }
    }

    public static int executeUpdateDelete(SQLiteDatabase db, SQLiteStatement stmt) {
        final long start;
        final String sql;
        final String debug;
        if (Logger.IS_DEBUG_ENABLED) {
        	sql = getSql(stmt);
            debug = getStatement(stmt, sql);
            Logger.debug(SqliteWrapper.class, "executeUpdateDelete: " + debug);
	        start = SystemClock.uptimeMillis();
        }

        int rows = 0;

        if (Build.VERSION.SDK_INT >= 11) {
        	rows = stmt.executeUpdateDelete();
        }
        else {
        	// have to call execute and then query number of affected rows
        	stmt.execute();
        	rows = getChanges(db);
        }

        if (Logger.IS_DEBUG_ENABLED) {
            final long delta = SystemClock.uptimeMillis() - start;
            Logger.debug(SqliteWrapper.class, "executeUpdateDelete: time = " + delta + "ms, stmt = " + debug
                    + ", returning " + rows);

            final String select = makeSelect(sql);
            if (select != null) {
            	checkQueryPlan(db, select);
            }
        }

        return rows;
    }

	public static int getChanges(SQLiteDatabase db) {
		int rows = 0;
		Cursor cursor = null;
		try {
			if (Logger.IS_DEBUG_ENABLED) {
			}
			cursor = db.rawQuery("SELECT changes()", null);
			if (cursor != null && cursor.moveToFirst()) {
				rows = cursor.getInt(0);
			}
			else {
				if (Logger.IS_ERROR_ENABLED) {
					Logger.error(SqliteWrapper.class, "getChanges: " +
						(cursor == null ? "null" : "empty") + " cursor");
				}
			}
		}
		finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return rows;
	}

	public static long executeInsert(SQLiteStatement stmt) {
        final long start;
        final String debug;
        if (Logger.IS_DEBUG_ENABLED) {
	        final String sql = getSql(stmt);
            debug = getStatement(stmt, sql);
            Logger.debug(SqliteWrapper.class, "executeInsert: " + debug);
	        start = SystemClock.uptimeMillis();
        }

        final long id = stmt.executeInsert();

        if (Logger.IS_DEBUG_ENABLED) {
            final long delta = SystemClock.uptimeMillis() - start;
            Logger.debug(SqliteWrapper.class, "executeInsert: time = " + delta + "ms, stmt = " + debug
                    + ", returning " + id);
        }

        return id;
    }

    public static long insert(SQLiteDatabase db, String table, ContentValues values) {
        return insertWithOnConflict(db, table, values, SQLiteDatabase.CONFLICT_NONE);
    }

    public static long insertWithOnConflict(SQLiteDatabase db, String table, ContentValues values,
            int conflictAlgorithm) {
        final long start;
        if (Logger.IS_DEBUG_ENABLED) {
            Logger.debug(SqliteWrapper.class, "insertWithOnConflict: table = " + table + ", values = " + values);
	        start = SystemClock.uptimeMillis();
        }
        final long id = db.insertWithOnConflict(table, null, values, conflictAlgorithm);

        if (Logger.IS_DEBUG_ENABLED) {
            final long delta = SystemClock.uptimeMillis() - start;
            Logger.debug(SqliteWrapper.class, "insertWithOnConflict: time = " + delta + "ms, table = " + table
                    + ", values = " + values + ", returning " + id);
        }

        return id;
    }

    public static int update(SQLiteDatabase db, String table, ContentValues values, String where, String[] whereArgs) {
        final long start;
        if (Logger.IS_DEBUG_ENABLED) {
            Logger.debug(SqliteWrapper.class, "update: table = " + table + ", where = <" + where + ">, args = "
                + truncate(whereArgs) + ", values = " + values);
	        start = SystemClock.uptimeMillis();
        }

        final int rows = db.update(table, values, where, whereArgs);

        if (Logger.IS_DEBUG_ENABLED) {
            final long delta = SystemClock.uptimeMillis() - start;
            Logger.debug(SqliteWrapper.class, "update: time = " + delta + "ms, table = " + table +
                ", where = <" + where + ">, args = " + truncate(whereArgs) +
                ", values = " + values + ", returning " + rows);

            checkQueryPlan(db, table, where);
        }

        return rows;
    }

    public static int delete(SQLiteDatabase db, String table, String where, String[] whereArgs) {
        final long start;
        if (Logger.IS_DEBUG_ENABLED) {
            Logger.debug(SqliteWrapper.class, "delete: table = " + table + ", where = <" + where + ">, args = " + truncate(whereArgs));
	        start = SystemClock.uptimeMillis();
        }

        final int rows = db.delete(table, where, whereArgs);

        if (Logger.IS_DEBUG_ENABLED) {
            final long delta = SystemClock.uptimeMillis() - start;
            Logger.debug(SqliteWrapper.class, "delete: time = " + delta + "ms, table = " + table +
                ", where = <" + where + ">, args = " + truncate(whereArgs) + ", returning " + rows);

            checkQueryPlan(db, table, where);
        }

        return rows;
    }

    public static Cursor rawQuery(SQLiteDatabase db, String query, String[] args) {
        final long start;
        final String params;

        if (Logger.IS_DEBUG_ENABLED) {
            params = "query = " + query + ", args = " + truncate(args);
            Logger.debug(SqliteWrapper.class, "rawQuery: " + params);
	        start = SystemClock.uptimeMillis();
        }

        try {
            final Cursor cursor = db.rawQuery(query, args);

            if (Logger.IS_DEBUG_ENABLED) {
                final long queryTime = SystemClock.uptimeMillis() - start;
                final String count = cursor == null ? "null" : Integer.toString(cursor.getCount());
                final long fillTime = SystemClock.uptimeMillis() - (start + queryTime);
                Logger.debug(SqliteWrapper.class, "rawQuery: query time = " + queryTime + "ms, fill time = "
                        + fillTime + "ms, " + params + ", returning " + count);

                checkQueryPlan(db, query);
            }

            return cursor;
        } catch (SQLiteException e) {
            if (Logger.IS_ERROR_ENABLED) {
                Logger.error(true, SqliteWrapper.class, "rawQuery:", e);
            }
            return null;
        }
    }

    public static void execSQL(SQLiteDatabase db, String sql) {
        final long start;
        if (Logger.IS_DEBUG_ENABLED) {
            Logger.debug(SqliteWrapper.class, "execSQL: sql = " + sql);
	        start = SystemClock.uptimeMillis();
        }

        db.execSQL(sql);

        if (Logger.IS_DEBUG_ENABLED) {
            final long delta = SystemClock.uptimeMillis() - start;
            Logger.debug(SqliteWrapper.class, "execSQL: time = " + delta + "ms, sql = " + sql);
        }
    }

    public static void analyze(SQLiteDatabase db) {
        execSQL(db, "ANALYZE");
    }

    private static String getSql(SQLiteStatement stmt) {
        return stmt.toString().replaceFirst("SQLiteProgram: ", "");
    }

    private static String getStatement(SQLiteStatement stmt, String sql) {
        final StringBuilder sb = new StringBuilder(sql);
        if (bindArgs != null) {
            try {
                final Object[] args = (Object[]) bindArgs.get(stmt);
                sb.append(", args = ");
                sb.append(args == null ? "null" : truncate(args));
            } catch (Throwable t) {
            }
        }
        return sb.toString();
    }

	public static void checkQueryPlan(SQLiteDatabase db, String orgQuery) {
		if (Logger.IS_DEBUG_ENABLED) {
			Cursor cursor = null;
			try {
				// normalize query and check for embedded params
				String query = spacePat.matcher(orgQuery).replaceAll(" ");

				// check for where clauses
				boolean embedded = false;
				Matcher whereMatcher = wherePat.matcher(query);
				if (whereMatcher.matches()) {
					String where = whereMatcher.group(2);

					Matcher matcher = unequalPat.matcher(where);
					if (matcher.find()) {
						where = matcher.replaceAll(" != ? ");
					}

					matcher = equalPat.matcher(where);
					if (matcher.find()) {
						embedded = true;
						where = matcher.replaceAll(" = ?");
					}

					query = whereMatcher.group(1) + where;
				}

				// check query plan on first query
				final boolean check;
				synchronized (queryMap) {
					if (check = !queryMap.contains(query)) {
						queryMap.add(query);
					}
				}

				if (check) {
					if (embedded) {
						Logger.warn(SqliteWrapper.class, "checkQueryPlan: query has embedded params: " + orgQuery);
					}
					cursor = db.rawQuery("EXPLAIN QUERY PLAN " + query, null);
					if (cursor != null) {
						final String prefix = "checkQueryPlan:   ";
						final StringBuilder sb = new StringBuilder(prefix);
						sb.append("query = ");
						sb.append(query);
						sb.append(", plan =\n");
						final int numCols = cursor.getColumnCount();
						final int lastRow = cursor.getCount() - 1;
						for (int row = 0; row <= lastRow; ++row) {
							cursor.moveToPosition(row);
							sb.append(prefix);
							for (int col = 0; col < numCols; ++col) {
								if (col > 0) {
									sb.append(' ');
								}
								sb.append(cursor.getString(col));
							}
							if (row < lastRow) {
								sb.append('\n');
							}
						}
						Logger.debug(SqliteWrapper.class, sb.toString());
					}
					else {
						Logger.error(SqliteWrapper.class, "checkQueryPlan: null cursor");
					}
				}
			}
			catch (Throwable t) {
				Logger.error(t);
			}
			finally {
				if (cursor != null) {
					cursor.close();
				}
			}
		}
	}

	public static void checkQueryPlan(SQLiteDatabase db, String table, String where) {
		if (Logger.IS_DEBUG_ENABLED) {
			checkQueryPlan(db, makeSelect(table, where));
		}
	}

	private static String makeSelect(String table, String where) {
		final StringBuilder query = new StringBuilder("SELECT * FROM ");
		query.append(table);
		if (where != null && where.length() != 0) {
			query.append(" WHERE ");
			query.append(where);
		}
		return query.toString();
	}

	private static String makeSelect(String updateOrDelete) {
		final Matcher matcher = updateOrDeletePat.matcher(updateOrDelete);
		if (matcher.matches()) {
			final String table = matcher.group(1);
			final String where = matcher.groupCount() == 1 ? null : matcher.group(2);
			return makeSelect(table, where);
		}
		Logger.warn(SqliteWrapper.class, "makeSelect: unable to parse <" + updateOrDelete + ">");
		return null;
	}

    public static String dumpRow(Cursor cursor) {
        if (cursor == null) {
            return "<null cursor>";
        }
        final StringBuilder sb = new StringBuilder("{");
        try {
            final String[] cols = cursor.getColumnNames();
            final int num = cols.length;
            for (int i = 0; i < num; ++i) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(cols[i]);
                sb.append(" = ");
                if (cursor.isNull(i)) {
                    sb.append("<null>");
                }
                else {
                    try {
                        sb.append(cursor.getString(i));
                    }
                    catch (Throwable t) {
                        sb.append("<error>");
                    }
                }
            }
        }
        catch (Throwable t) {
            if (Logger.IS_ERROR_ENABLED) {
                Logger.error(SqliteWrapper.class, "dumpRow:", t);
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static String truncate(Object[] array) {
        return Arrays.toString(array);
    }
}
