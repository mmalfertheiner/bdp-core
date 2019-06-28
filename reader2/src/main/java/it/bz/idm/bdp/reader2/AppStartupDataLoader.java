package it.bz.idm.bdp.reader2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.jsoniter.output.JsonStream;

import it.bz.idm.bdp.reader2.utils.jsonserializer.JsonIterPostgresSupport;
import it.bz.idm.bdp.reader2.utils.jsonserializer.JsonIterSqlTimestampSupport;
import it.bz.idm.bdp.reader2.utils.querybuilder.QueryBuilder;
import it.bz.idm.bdp.reader2.utils.querybuilder.SelectExpansion;
import it.bz.idm.bdp.reader2.utils.queryexecutor.ColumnMapRowMapper;
import it.bz.idm.bdp.reader2.utils.queryexecutor.QueryExecutor;


@Component
public class AppStartupDataLoader implements ApplicationListener<ContextRefreshedEvent> {

	@Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    private boolean alreadySetup = false;

	@Override
	public void onApplicationEvent(ContextRefreshedEvent arg0) {
		if (alreadySetup) {
            return;
        }

		boolean ignoreNull = false;

		SelectExpansion se = new SelectExpansion();

		se.addColumn("measurement", "mvalidtime", "me.timestamp");
		se.addColumn("measurement", "mtransactiontime", "me.created_on");
		se.addColumn("measurement", "mperiod", "me.period");
		se.addColumn("measurement", "mvalue", "me.double_value");

		se.addColumn("datatype", "tname", "t.cname");
		se.addColumn("datatype", "tunit", "t.cunit");
		se.addColumn("datatype", "ttype", "t.rtype");
		se.addColumn("datatype", "tdescription", "t.description");
		se.addSubDef("datatype", "tmeasurements", "measurement");

		se.addColumn("parent", "pname", "p.name");
		se.addColumn("parent", "ptype", "p.stationtype");
		se.addColumn("parent", "pcoordinate", "p.pointprojection");
		se.addColumn("parent", "pcode", "p.stationcode");
		se.addColumn("parent", "porigin", "p.origin");
		se.addColumn("parent", "pmetadata", "pm.json");

		se.addColumn("station", "sname", "s.name");
		se.addColumn("station", "stype", "s.stationtype");
		se.addColumn("station", "scode", "s.stationcode");
		se.addColumn("station", "sorigin", "s.origin");
		se.addColumn("station", "sactive", "s.active");
		se.addColumn("station", "savailable", "s.available");
		se.addColumn("station", "scoordinate", "s.pointprojection");
		se.addColumn("station", "smetadata", "m.json");
		se.addSubDef("station", "sparent", "parent");
		se.addSubDef("station", "sdatatypes", "datatype");

		/* Define where-clause items and their mappings to SQL */
		se.addOperator("value", "eq", "= %s");
		se.addOperator("value", "neq", "<> %s");
		se.addOperator("null", "eq", "is null");
		se.addOperator("null", "neq", "is not null");
		se.addOperator("value", "lt", "< %s");
		se.addOperator("value", "gt", "> %s");
		se.addOperator("value", "lteq", "=< %s");
		se.addOperator("value", "gteq", ">= %s");
		se.addOperator("value", "re", "~ %s");
		se.addOperator("value", "ire", "~* %s");
		se.addOperator("value", "nre", "!~ %s");
		se.addOperator("value", "nire", "!~* %s");
		se.addOperator("list", "in", "in (%s)", t -> {
			return !(t.getChildren().size() == 1 && t.getChild("value").getValue() == null);
		});
		se.addOperator("list", "bbi", "&& ST_MakeEnvelope(%s)", t -> {
			return t.getChildren().size() == 4 || t.getChildren().size() == 5;
		});
		se.addOperator("list", "bbc", "@ ST_MakeEnvelope(%s)", t -> {
			return t.getChildren().size() == 4 || t.getChildren().size() == 5;
		});

		/* Set the query builder, JDBC template's row mapper and JSON parser up */
		QueryBuilder.setup(se);
		QueryExecutor.setup(jdbcTemplate);

		// The API should have a flag to remove null values (what should be default? <-- true)
		ColumnMapRowMapper.setIgnoreNull(ignoreNull);
		JsonStream.setIndentionStep(4);
//		JsonIterUnicodeSupport.enable();
		JsonIterSqlTimestampSupport.enable("yyyy-MM-dd HH:mm:ss.SSSZ");
		JsonIterPostgresSupport.enable();
	}

}

