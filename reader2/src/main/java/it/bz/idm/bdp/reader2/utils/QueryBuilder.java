package it.bz.idm.bdp.reader2.utils;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.jsoniter.output.JsonStream;

/**
 * Create an instance of TypedQuery for executing a Java Persistence query language statement.
 * This is a convenience class, that supports conditional query statements and emulates getSingleResult
 * without not-found or non-unique-result exceptions.
 *
 * @author Peter Moser
 */
public class QueryBuilder {
	private StringBuilder sql = new StringBuilder();
	private static NamedParameterJdbcTemplate npjt;
	private static SelectExpansion se;
	private MapSqlParameterSource parameters = new MapSqlParameterSource();

	public Set<String> columnAliases;
	public Map<String, String> exp;

	public QueryBuilder(final String select, String... selectDefNames) {
		columnAliases = se.getColumnAliases(select, selectDefNames);
		exp = se._expandSelect(columnAliases, selectDefNames);
	}

	/**
	 * Create a new {@link QueryBuilder} instance
	 *
	 * @see QueryBuilder#QueryBuilder(EntityManager)
	 *
	 * @param namedParameterJdbcTemplate {@link EntityManager}
	 */
	public static synchronized void setup(NamedParameterJdbcTemplate namedParameterJdbcTemplate, SelectExpansion selectExpansion) {
		if (selectExpansion == null || namedParameterJdbcTemplate == null) {
			throw new RuntimeException("No SelectExpansion or NamedParameterJdbcTemplate defined!");
		}
		if (QueryBuilder.npjt != null || QueryBuilder.se != null) {
			throw new RuntimeException("QueryBuilder.setup can only be called once");
		}
		QueryBuilder.npjt = namedParameterJdbcTemplate;
		QueryBuilder.se = selectExpansion;
	}

	public static QueryBuilder init(final String select, String... selectDefNames) {
		if (QueryBuilder.npjt == null) {
			throw new RuntimeException("Missing JDBC template. Run QueryBuilder.setup before initialization.");
		}
		if (QueryBuilder.se == null) {
			throw new RuntimeException("Missing Select Expansion. Run QueryBuilder.setup before initialization.");
		}
		return new QueryBuilder(select, selectDefNames);
	}

	public static QueryBuilder init(NamedParameterJdbcTemplate namedParameterJdbcTemplate, SelectExpansion selectExpansion, final String select, String... selectDefNames)  {
		QueryBuilder.setup(namedParameterJdbcTemplate,selectExpansion);
		return QueryBuilder.init(select, selectDefNames);
	}

	/**
	 * Set a parameter with <code>name</code> and <code>value</code> and add
	 * <code>sqlPart</code> to the end of the SQL string, if the
	 * <code>condition</code> holds.
	 *
	 * @param name of the parameter
	 * @param value of the parameter
	 * @param sqlPart SQL string
	 * @param condition that must hold
	 * @return {@link QueryBuilder}
	 */
	public QueryBuilder setParameterIfNotNull(String name, Object value, String sqlPart) {
		return setParameterIfNotNullAnd(name, value, sqlPart, true);
	}

	public QueryBuilder setParameterIfNotNullAnd(String name, Object value, String sqlPart, boolean condition) {
		return setParameterIf(name, value, sqlPart, value != null && condition);
	}

	public QueryBuilder setParameterIfNotEmpty(String name, Object value, String sqlPart) {
		return setParameterIfNotEmptyAnd(name, value, sqlPart, true);
	}

	@SuppressWarnings("rawtypes")
	public QueryBuilder setParameterIfNotEmptyAnd(String name, Object value, String sqlPart, boolean condition) {
		return setParameterIf(name, value, sqlPart, value != null
													&& (value instanceof Collection)
													&& !((Collection)value).isEmpty()
													&& condition);
	}

	/**
	 * Set a parameter with <code>name</code> and <code>value</code> and add
	 * <code>sqlPart</code> to the end of the SQL string, if the
	 * <code>condition</code> holds.
	 *
	 * @param name of the parameter
	 * @param value of the parameter
	 * @param sqlPart SQL string
	 * @param condition that must hold
	 * @return {@link QueryBuilder}
	 */
	public QueryBuilder setParameterIf(String name, Object value, String sqlPart, boolean condition) {
		if (condition) {
			addSql(sqlPart);
			setParameter(name, value);
		}
		return this;
	}

	/**
	 * Set a parameter with <code>name</code> and <code>value</code>, if
	 * it is not null or empty.
	 *
	 * @param name of the parameter
	 * @param value of the parameter
	 * @return {@link QueryBuilder}
	 */
	public QueryBuilder setParameter(String name, Object value) {
		if (name != null && !name.isEmpty()) {
			parameters.addValue(name, value);
		}
		return this;
	}

	/**
	 * Build the current query and execute it via {@link NamedParameterJdbcTemplate#query}
	 *
	 * @return A list of (key, value) pairs
	 */
	public List<Map<String, Object>> build() {
		return npjt.query(sql.toString(), parameters, new ColumnMapRowMapper());
	}

	public <T> List<T> build(Class<T> resultClass) {
		return npjt.queryForList(sql.toString(), parameters, resultClass);
	}

	/**
	 * Build the current query and execute it via {@link NamedParameterJdbcTemplate#query},
	 * finally serialize it into a JSON string
	 *
	 * @return List of <code>resultClass</code> objects
	 */
	public String buildJson() {
		return JsonStream.serialize(build());
	}

	public <T> String buildJson(Class<T> resultClass) {
		return JsonStream.serialize(build(resultClass));
	}

	/**
	 * Emulate getSingleResult without not-found or non-unique-result exceptions. Simply
	 * return null, if {@link javax.persistence.TypedQuery#getResultList} has no results,
	 * and leave exceptions to proper errors.
	 *
	 * @param resultClass Type of the query result
	 * @return topmost result or null if not found
	 */
	public <T> T buildSingleResultOrNull(Class<T> resultClass) {
		return buildSingleResultOrAlternative(resultClass, null);
	}

	/**
	 * Emulate getSingleResult without not-found or non-unique-result exceptions. Simply
	 * return <code>alternative</code>, if {@link javax.persistence.TypedQuery#getResultList}
	 * has no results, and leave exceptions to proper errors.
	 *
	 * @param resultClass Type of the query result
	 * @param alternative to be returned, if {@link javax.persistence.TypedQuery#getResultList} does not return results
	 * @return topmost result or 'alternative' if not found
	 */
	public <T> T buildSingleResultOrAlternative(Class<T> resultClass, T alternative) {
		List<T> list = build(resultClass);
		if (list == null || list.isEmpty()) {
			return alternative;
		}
		return list.get(0);
	}

	/**
	 * Append <code>sqlPart</code> to the end of the SQL string.
	 * @param sqlPart SQL string
	 * @return {@link QueryBuilder}
	 */
	public QueryBuilder addSql(String sqlPart) {
		if (sqlPart != null && !sqlPart.isEmpty()) {
			sql.append(" ");
			sql.append(sqlPart);
		}
		return this;
	}

	/**
	 * Append <code>sqlPart</code> to the end of the SQL string, if
	 * <code>condition</code> holds.
	 *
	 * @param sqlPart SQL string
	 * @return {@link QueryBuilder}
	 */
	public QueryBuilder addSqlIf(String sqlPart, boolean condition) {
		if (sqlPart != null && !sqlPart.isEmpty() && condition) {
			sql.append(" ");
			sql.append(sqlPart);
		}
		return this;
	}

	public QueryBuilder addSqlIfAlias(String sqlPart, String alias) {
		if (sqlPart != null && !sqlPart.isEmpty() && columnAliases.contains(alias)) {
			sql.append(" ");
			sql.append(sqlPart);
		}
		return this;
	}

	public QueryBuilder addSqlIfDefinition(String sqlPart, String selectDefName) {
		if (sqlPart != null && !sqlPart.isEmpty() && exp.containsKey(selectDefName)) {
			sql.append(" ");
			sql.append(sqlPart);
		}
		return this;
	}

	/**
	 * Append <code>sqlPart</code> to the end of the SQL string, if
	 * <code>object</code> is not null.
	 *
	 * @param sqlPart SQL string
	 * @return {@link QueryBuilder}
	 */
	public QueryBuilder addSqlIfNotNull(String sqlPart, Object object) {
		return addSqlIf(sqlPart, object != null);
	}

	/**
	 * Appends all <code>sqlPart</code> elements to the end of the SQL string.
	 *
	 * @param sqlPart SQL string array
	 * @return {@link QueryBuilder}
	 */
	public QueryBuilder addSql(String... sqlPart) {
		for (int i = 0; i < sqlPart.length; i++) {
			addSql(sqlPart[i]);
		}
		return this;
	}

	public QueryBuilder addLimit(long limit) {
		setParameterIf("limit", new Long(limit), "limit :limit", limit > 0);
		return this;
	}

	public QueryBuilder addOffset(long offset) {
		setParameterIf("offset", new Long(offset), "offset :offset", offset >= 0);
		return this;
	}

	public QueryBuilder expandSelect(final String... selectDef) {
		if (selectDef != null && selectDef.length > 0) {
			for (String def : selectDef) {
				String expansion = exp.get(def);
				if (expansion != null) {
					sql.append(", ");
					sql.append(expansion);
				}
			}
		}
		return this;
	}

	public QueryBuilder expandSelect(boolean condition, final String... selectDef) {
		if (condition) {
			return expandSelect(selectDef);
		}
		return this;
	}

	public QueryBuilder expandSelect(String select, Map<String, Object> selectDef, boolean recurse, boolean optional) {
		addSql(_expandSelect(select, selectDef, recurse, optional));
		return this;
	}

	@SuppressWarnings("unchecked")
	public static String _expandSelect(String select, Map<String, Object> selectDef, boolean recurse, boolean optional) {
		StringBuffer sb = new StringBuffer();
		Set<String> columnAliases;
		if (!optional && (select == null || select.trim().equals("*"))) {
			columnAliases = selectDef.keySet();
		} else {
			columnAliases = csvToSet(select);
		}

		for (String columnAlias : columnAliases) {
			if (!selectDef.containsKey(columnAlias)) {
				throw new RuntimeException("Key '" + columnAlias + "' does not exist!");
			}
			Object def = selectDef.get(columnAlias);
			if (def == null)
				continue;
			if (def instanceof String) {
				sb.append(def)
				  .append(" as ")
				  .append(columnAlias)
				  .append(", ");
			} else if (def instanceof Map) {
				if (recurse) {
					sb.append(_expandSelect(null, (Map<String, Object>) def, recurse, optional))
					  .append(", ");
				}
			} else {
				throw new RuntimeException("A select definition must contain either Strings or Maps!");
			}
		}
		return sb.length() >= 3 ? sb.substring(0, sb.length() - 2) : sb.toString();
	}

	public static Set<String> csvToSet(final String csv) {
		Set<String> resultSet = new HashSet<String>();
		for (String value : csv.split(",")) {
			value = value.trim();
			if (value.equals("*")) {
				resultSet.clear();
				resultSet.add(value);
				return resultSet;
			}
			resultSet.add(value);
		}
		return resultSet;
	}

	public StringBuilder getSql() {
		return sql;
	}

	public SelectExpansion getSelectExpansion() {
		return se;
	}

}
