package com.bartonsoft.test;

import java.util.Arrays;

import android.database.sqlite.SQLiteDatabase;

import com.bartonsoft.logger.Logger;
import com.bartonsoft.util.SqliteWrapper;

/**
 * Abstract superclass of all tables.
 */
public abstract class Table {
	public enum DataType {
		INTEGER,
		TEXT,
		REAL,
		BLOB
	}

	public enum Conflict {
		ROLLBACK,
		ABORT,
		FAIL,
		IGNORE,
		REPLACE
	}

	public enum Constraint {
		PRIMARY_KEY("PRIMARY KEY", null),
		AUTOINCREMENT("PRIMARY KEY AUTOINCREMENT", null),
		UNIQUE_ROLLBACK("UNIQUE", Conflict.ROLLBACK),
		NOT_NULL("NOT NULL", null),
		NOT_NULL_ROLLBACK("NOT NULL", Conflict.ROLLBACK),
		DEFAULT_ZERO("DEFAULT 0", null),
		COLLATE_NOCASE("COLLATE NOCASE", null);

		private final String sql;
		private final Conflict conflict;

		Constraint(String sql, Conflict conflict) {
			this.sql = sql;
			this.conflict = conflict;
		}

		private void append(StringBuilder sb) {
			append(sb, null);
		}

		private void append(StringBuilder sb, String[] cols) {
			sb.append(' ');
			sb.append(sql);
			if (cols != null) {
				sb.append(" (");
				final int num = cols.length;
				for (int i = 0; i < num; ++i) {
					if (i > 0) {
						sb.append(", ");
					}
					sb.append('\'');
					sb.append(cols[i]);
					sb.append('\'');
				}
				sb.append(")");
			}
			if (conflict != null) {
				sb.append(" ON CONFLICT ");
				sb.append(conflict.name());
			}
		}
	}

	public static class TableConstraint {
		private final Constraint constraint;
		private final String[] cols;

		public TableConstraint(Constraint constraint, String... cols) {
			this.constraint = constraint;
			this.cols = cols;
		}

		private void append(StringBuilder sb) {
			constraint.append(sb, cols);
		}
	}

	public static class ColumnDef {
		private final String name;
		private final DataType type;
		private final Constraint[] constraints;

		/**
		 * Defines a column in a table.
		 *
		 * @param name the column name
		 * @param type the column type
		 * @param constraints the set of column constraints, or null if none
		 */
		public ColumnDef(String name, DataType type, Constraint... constraints) {
			this.name = name;
			this.type = type;
			this.constraints = constraints;
		}

		private void append(StringBuilder sb) {
			sb.append('\'');
			sb.append(name);
			sb.append('\'');
			sb.append(" ");
			sb.append(type.name());
			if (constraints != null) {
				for (Constraint constraint : constraints) {
					constraint.append(sb);
				}
			}
		}
	}

	public static class Index {
		private final Table table;
		private final boolean unique;
		private final String[] cols;

		/**
		 * Defines an index on a column.
		 *
		 * @param table the table being indexed
		 * @param unique true if the index is unique
		 * @param cols the ordered list of column names in the index
		 */
		public Index(Table table, boolean unique, String... cols) {
			this.table = table;
			this.unique = unique;
			this.cols = cols;
		}

		private String getName() {
			final StringBuilder sb = new StringBuilder();
			getName(sb);
			return sb.toString();
		}

		private void getName(StringBuilder sb) {
			sb.append(table.getTableName());
			final int numCols = cols.length;
			for (int i = 0; i < numCols; ++i) {
				sb.append('_');
				sb.append(cols[i]);
			}
		}

		private String getSql(String table, boolean ignore) {
			final StringBuilder sb = new StringBuilder(512);
			sb.append("CREATE ");
			if (unique) {
				sb.append("UNIQUE ");
			}
			sb.append("INDEX ");
			if (ignore) {
				sb.append("IF NOT EXISTS ");
			}
			getName(sb);
			sb.append(" ON ");
			sb.append(table);

			sb.append(" (");
			final int numCols = cols.length;
			for (int i = 0; i < numCols; ++i) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.append('\'');
				sb.append(cols[i]);
				sb.append('\'');
			}
			sb.append(")");
			return sb.toString();
		}
	}


	/**
	 * Returns the name of the table.
	 */
	protected abstract String getTableName();

	/**
	 * Returns the set of column definitions for the table.
	 */
	protected abstract ColumnDef[] getColumnDefs();

	/**
	 * Returns the name of the virtual module used for the table, or null if none.
	 */
	protected String getVirtualModule() {
		return null;
	}

	/**
	 * Returns the set of table constraints, or null if none.
	 */
	protected TableConstraint[] getConstraints() {
		return null;
	}

	/**
	 * Returns the set of indexes for the table, or null if none.
	 */
	protected Index[] getIndexes() {
		return null;
	}

	/**
	 * Creates the table and its indexes.
	 *
	 * @param db the database
	 * @param ignore if true then errors are ignored
	 */
	public void createTable(SQLiteDatabase db, boolean ignore) {
		createTable(db, getTableName(), ignore);
		createIndexes(db, ignore);
	}

	private void createTable(SQLiteDatabase db, String table, boolean ignore) {
		final StringBuilder sb = new StringBuilder(512);
		sb.append("CREATE ");
		final String mod = getVirtualModule();
		if (mod != null) {
			sb.append("VIRTUAL ");
		}
		sb.append("TABLE ");
		if (ignore) {
			sb.append("IF NOT EXISTS ");
		}
		sb.append('\'');
		sb.append(table);
		sb.append('\'');
		if (mod != null) {
			sb.append(" USING ");
			sb.append(mod);
		}

		// column defs
		sb.append(" (");
		final ColumnDef[] cols = getColumnDefs();
		final int num = cols.length;
		for (int i = 0; i < num; ++i) {
			if (i > 0) {
				sb.append(", ");
			}
			cols[i].append(sb);
		}

		// constraints
		final TableConstraint[] constraints = getConstraints();
		if (constraints != null) {
			for (TableConstraint constraint : constraints) {
				sb.append(",");
				constraint.append(sb);
			}
		}

		sb.append(")");

		Logger.debug(sb.toString());
	}

	/**
	 * Creates the table's indexes.
	 *
	 * @param db the database
	 * @param ignore if true then errors are ignored
	 */
	private void createIndexes(SQLiteDatabase db, boolean ignore) {
		final Index[] indexes = getIndexes();
		if (indexes != null) {
			for (Index index : indexes) {
				createIndex(db, index, ignore);
			}
		}
	}

	private void createIndex(SQLiteDatabase db, Index index, boolean ignore) {
		Logger.debug(index.getSql(getTableName(), ignore));
	}


	/*
	 * Upgrade methods
	 */


	/**
	 * Drops the table.
	 */
	public void drop(SQLiteDatabase db) {
		SqliteWrapper.execSQL(db, "DROP TABLE IF EXISTS " + getTableName());
	}

	/**
	 * Adds the given columns to the table.
	 */
	void addCols(SQLiteDatabase db, String[] colNames) {
		for (String colName : colNames) {
			final StringBuilder sb = new StringBuilder(512);
			sb.append("ALTER TABLE ");
			sb.append(getTableName());
			sb.append(" ADD COLUMN ");

			// add the column def
			for (ColumnDef col : getColumnDefs()) {
				if (colName == col.name) {
					col.append(sb);
					break;
				}
			}

			SqliteWrapper.execSQL(db, sb.toString());
		}
	}

	/**
	 * Adds the given indexes to the table.
	 *
	 * @param db the database
	 * @param addedIndexCols the set of indexes to create, each of which consists of the names of the columns to index
	 */
	void addIndexes(SQLiteDatabase db, String[][] addedIndexCols) {
		final Index[] indexes = getIndexes();
		if (indexes != null) {
			for (String[] indexCols : addedIndexCols) {
				Index foundIndex = null;
				for (Index index : indexes) {
					if (Arrays.equals(indexCols, index.cols)) {
						foundIndex = index;
						break;
					}
				}
				if (foundIndex != null) {
					createIndex(db, foundIndex, true);
				}
				else if (Logger.IS_DEBUG_ENABLED) {
					throw new UnsupportedOperationException("Unable to find index " + Arrays.toString(indexCols));
				}
			}
		}
		else if (Logger.IS_DEBUG_ENABLED) {
			throw new UnsupportedOperationException("Trying to add index for table with no indexes");
		}
	}

	private void dropIndex(SQLiteDatabase db, Index index) {
		final String sql = "DROP INDEX IF EXISTS " + index.getName();
		SqliteWrapper.execSQL(db, sql);
	}

	void dropIndexes(SQLiteDatabase db, Table table, String[][] droppedIndexCols) {
		for (String[] indexCols : droppedIndexCols) {
			dropIndex(db, new Index(table, false, indexCols));
		}
	}

	void alter(SQLiteDatabase db, String[] updates) {
		// copy the table and apply updates
		final String table = getTableName();
		final String newTable = "new_" + table;
		createTable(db, newTable, false);

		final StringBuilder sb = new StringBuilder(512);
		sb.append("INSERT INTO ");
		sb.append(newTable);
		sb.append(" SELECT ");
		final ColumnDef[] cols = getColumnDefs();
		final int num = cols.length;
		for (int i = 0; i < num; ++i) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(cols[i].name);
		}
		sb.append(" FROM ");
		sb.append(table);
		SqliteWrapper.execSQL(db, sb.toString());

		if (updates != null) {
			for (String update : updates) {
				sb.delete(0, sb.length());
				sb.append("UPDATE ");
				sb.append(newTable);
				sb.append(' ');
				sb.append(update);
				SqliteWrapper.execSQL(db, sb.toString());
			}
		}

		drop(db);

		sb.delete(0, sb.length());
		sb.append("ALTER TABLE ");
		sb.append(newTable);
		sb.append(" RENAME TO ");
		sb.append(table);
		SqliteWrapper.execSQL(db, sb.toString());

		createIndexes(db, false);
	}
}
