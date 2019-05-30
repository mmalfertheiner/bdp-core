package it.bz.idm.bdp.reader2.utils.querybuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SelectDefinition {

	private final String name;
	private Map<String, String> columns = new HashMap<String, String>();
	private Map<String, SelectDefinition> pointers = new HashMap<String, SelectDefinition>();
	private Set<String> aliases = new HashSet<String>();

	public SelectDefinition(final String name) {
		if (name == null || name.isEmpty()) {
			throw new RuntimeException("A select definition's name must be set!");
		}
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void addAlias(final String alias, final String column) {
		if (alias == null || alias.isEmpty() || column == null || column.isEmpty()) {
			throw new RuntimeException("A select definition's alias must have a name and a column!");
		}
		if (aliases.contains(alias)) {
			throw new RuntimeException("Alias '" + alias + "' already exists!");
		}
		aliases.add(alias);
		columns.put(alias, column);
	}

	public void addPointer(final String alias, final SelectDefinition selectDefinition) {
		if (alias == null || alias.isEmpty() || selectDefinition == null) {
			throw new RuntimeException("A select definition's pointer must have a name and a valid sub-definition!");
		}
		if (aliases.contains(alias)) {
			throw new RuntimeException("Alias '" + alias + "' already exists!");
		}
		aliases.add(alias);
		pointers.put(alias, selectDefinition);
	}

	public SelectDefinition getSelectDefinition(final String alias) {
		return pointers.get(alias);
	}

	public String getColumn(final String alias) {
		return columns.get(alias);
	}

	private Object _get(final String alias) {
		Object res = columns.get(alias);
		if (res == null) {
			res = pointers.get(alias);
		}
		return res;
	}

	public boolean exists(final String alias) {
		return _get(alias) != null;
	}

	public boolean isSelectDefinition(final String alias) {
		Object res = _get(alias);
		return res != null && res instanceof SelectDefinition;
	}

	public boolean isColumn(final String alias) {
		Object res = _get(alias);
		return res != null && res instanceof String;
	}

	public Map<String, String> getColumnsOnly() {
		return columns;
	}

	public Map<String, SelectDefinition> getPointersOnly() {
		return pointers;
	}

	public Set<String> getAliases() {
		return aliases;
	}

	@Override
	public String toString() {
		return "SelectDefinition [name=" + name + ", columns=" + columns + ", pointers=" + pointers + "]";
	}

}
