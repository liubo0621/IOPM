package cn.com.pattek.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.log4j.Logger;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.nlpcn.es4sql.domain.Select;
import org.nlpcn.es4sql.exception.SqlParseException;
import org.nlpcn.es4sql.parse.ElasticSqlExprParser;
import org.nlpcn.es4sql.parse.SqlParser;
import org.nlpcn.es4sql.query.AggregationQueryAction;
import org.nlpcn.es4sql.query.DefaultQueryAction;
import org.nlpcn.es4sql.query.SqlElasticSearchRequestBuilder;

import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLQueryExpr;
import com.alibaba.druid.sql.parser.ParserException;
import com.alibaba.druid.sql.parser.SQLExprParser;
import com.alibaba.druid.sql.parser.Token;


import cn.com.pattek.RelatedNews.entity.ArticleInfo;
import cn.com.pattek.RelatedNews.entity.AtCount;

public class ESSearchUtils {
	private static String ipPort;
	private static String clusterName;
	public static ESSearchUtils esSearchUtils;
	
	static {
//		PropertiesFactory propertiesFactory = new PropertiesFactory();
		ipPort = PropertiesFactory.getValue("es.hosts");
		clusterName = PropertiesFactory.getValue("clusterName");
//		ipPort="192.168.60.30:9300";
//		clusterName="elasticsearch";
	}

	
	protected Logger log = Logger.getLogger("db");
	
	
	private Client client;

	public Client getClient() {
		return client;
	}

	public void setClient(Client client) {
		this.client = client;
	}
	
	public ESSearchUtils(){
		String []ipPorts = this.ipPort.split(",");
		String clusterName = this.clusterName;
		try {
			init(ipPorts , clusterName);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static ESSearchUtils getInstance(){
		if (esSearchUtils == null){
			esSearchUtils = new ESSearchUtils();
		}
		
		return esSearchUtils;
	}

//	public ESSearchUtils(String ip , int port) throws UnknownHostException {
//		Client client = TransportClient.builder().build()
//				.addTransportAddress(
//						new InetSocketTransportAddress(InetAddress
//								.getByName(ip), port));
//
//		this.client = client;
//	}
	
	public void init(String[] ipPorts, String clusterName) throws UnknownHostException {
		Settings settings = Settings.settingsBuilder().put("client.transport.sniff", true)// 客户端嗅探整个集群的状态，把集群中其它机器的ip地址自动添加到客户端中，并且自动发现新加入集群的机器
				.put("client", true)// 仅作为客户端连接
				.put("cluster.name", clusterName).build();// 集群名称
		Client client = TransportClient.builder().settings(settings).build();
		if (ipPorts != null) {
			for (String host : ipPorts) {
				if (host.contains(":")) {
					String ip = host.split(":")[0];
					int port = new Integer(host.split(":")[1]);
					((TransportClient) client).addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(ip), port));
				}
			}
		}
		
		this.client = client;
	}
	
	
	public void closeClient(){
		//单利模式不需要关闭
//		this.client.close();
	}

	public List<Object> search(String indexName, Map<String, String> map,
			int start, int end) {
		List<Object> list = new ArrayList<Object>();
		List<ArticleInfo> data = new ArrayList<ArticleInfo>();
		int number = 0;
		Set<String> set = map.keySet();
		Iterator<String> iter = set.iterator();
		BoolQueryBuilder bqb = QueryBuilders.boolQuery();
		while (iter.hasNext()) {
			String key = iter.next();
			String value = map.get(key);
			if ("EMOTION".equals(key) || "INFO_TYPE".equals(key)) {
				// || "RELEASE_TIME".equals(key) || "RECORD_TIME".equals(key)) {
				String[] str = value.trim().split(",");
				bqb.filter(QueryBuilders.termsQuery(key, str));
			} else if ("SORT".equals(key)) {
				// 排序相关
			} else if ("SPREAD".equals(key)) {
				bqb.must(QueryBuilders.matchPhraseQuery("IS_VIP", value));
			} else if ("KEYWORDS".equals(key)) {
				String[] str = value.trim().split(",");
				bqb.filter(QueryBuilders.termsQuery("TITLE", str));
				bqb.filter(QueryBuilders.termsQuery("CONTENT", str));
			} else {
				bqb.must(QueryBuilders.matchPhraseQuery(key, value));
			}
		}

		SearchResponse response = client.prepareSearch(indexName).setTypes(
				indexName).setQuery(bqb).setFrom(start).setSize(end).execute()
				.actionGet();
		System.out.println("结果条数：" + response.getHits().getTotalHits());
		number = (int) response.getHits().getTotalHits();
		for (SearchHit hits : response.getHits()) {
			// System.out.println(hits.getSourceAsString());
			data.add(printJSON(hits.getSourceAsString()));
		}
		list.add(number);
		list.add(data);
		return list;
	}

	/**
	 * 根据传入的map，解析为sql语句进行查询
	 * 
	 * @param map
	 * @param start
	 *            分页开始
	 * @param end
	 *            分页结束
	 * @return
	 * @throws Exception
	 */
	public List<Object> searchBySql(Map<String, String> map, int start, int end)
			throws Exception {
		/**1*/
		String cluesSql = "";
		String ids = "";
		//先查询map中有没有分类字段，有的话先查询出id集合
		String zeroIdStr = map.get("ZERO_ID");
		String firstIdStr = map.get("FIRST_CLASSIFY_ID");
		String secondIdStr = map.get("SECOND_CLASSIFY_ID");
		if (zeroIdStr != null && !"".equals(zeroIdStr)) {
			cluesSql = "select ID from tab_iopm_clues t where ZERO_ID=" + zeroIdStr;
			
			if (firstIdStr != null && !"".equals(firstIdStr)) {
				cluesSql += " and FIRST_ID=" + firstIdStr;
				
				if (secondIdStr != null && !"".equals(secondIdStr)) {
					cluesSql += " and SECOND_ID=" + secondIdStr;
				}
			}
		
			SQLExprParser parser2 = new ElasticSqlExprParser(cluesSql);
			SQLExpr expr2 = parser2.expr();
			if (parser2.getLexer().token() != Token.EOF) {
				throw new ParserException("illegal sql expr : " + cluesSql);
			}
			SQLQueryExpr queryExpr2 = (SQLQueryExpr) expr2;
			// 通过抽象语法树，封装成自定义的Select，包含了select、from、where group、limit等
			Select select2 = new SqlParser().parseSelect(queryExpr2);
	
			AggregationQueryAction action2;
			DefaultQueryAction queryAction2 = null;
			if (select2.isAgg) {
				// 包含计算的的排序分组的
				// request.setSearchType(SearchType.DEFAULT);
				action2 = new AggregationQueryAction(client, select2);
			} else {
				// 封装成自己的Select对象
				queryAction2 = new DefaultQueryAction(client, select2);
			}
			// 把属性封装在SearchRequestBuilder(client.prepareSearch()获取的即ES中获取的方法)对象中
			// 然后装饰了一下SearchRequestBuilder为自定义的SqlElasticSearchRequestBuilder
			SqlElasticSearchRequestBuilder requestBuilder2 = queryAction2.explain();
			// 之后就是对ES的操作
			SearchResponse response2 = (SearchResponse) requestBuilder2.get();
	
			System.out.println("结果条数：" + response2.getHits().getTotalHits());
			for (SearchHit hits : response2.getHits()) {
				// System.out.println(hits.getSourceAsString());
				ids +=printJSON2(hits.getSourceAsString());
				
				
			}
		}
		System.out.println(ids);
		if (!"".equals(ids)) {
//			String[] idsArr = ids.trim().split(",");
			map.put("IDSARR", ids);
		} else if (zeroIdStr != null && !"".equals(zeroIdStr)){
			map.put("IDSARR", "");
		}
		
		/**2*/
		List<Object> list = new ArrayList<Object>();
		List<ArticleInfo> data = new ArrayList<ArticleInfo>();
		int number = 0;

		String sql = map2String(map);
		//if zeroIdStr is null 说明 没有查到ids，
//		if (zeroIdStr != null && !"".equals(zeroIdStr) && "".equals(ids)){
//			sql += " and CLUES_IDS in ()";
//		}
		if (start != 0 && end != 0) {
			sql += " limit " + (start - 1) * 20 + "," + end;
		}
		// 其中采用的是阿里的druid框架，
		// 其中ElasticLexer和ElasticSqlExprParser都是对druid中的MySql的进行了扩展
		SQLExprParser parser = new ElasticSqlExprParser(sql);
		SQLExpr expr = parser.expr();
		if (parser.getLexer().token() != Token.EOF) {
			throw new ParserException("illegal sql expr : " + sql);
		}
		SQLQueryExpr queryExpr = (SQLQueryExpr) expr;
		// 通过抽象语法树，封装成自定义的Select，包含了select、from、where group、limit等
		Select select = new SqlParser().parseSelect(queryExpr);

		AggregationQueryAction action;
		DefaultQueryAction queryAction = null;
		if (select.isAgg) {
			// 包含计算的的排序分组的
			// request.setSearchType(SearchType.DEFAULT);
			action = new AggregationQueryAction(client, select);
		} else {
			// 封装成自己的Select对象
			queryAction = new DefaultQueryAction(client, select);
		}
		// 把属性封装在SearchRequestBuilder(client.prepareSearch()获取的即ES中获取的方法)对象中
		// 然后装饰了一下SearchRequestBuilder为自定义的SqlElasticSearchRequestBuilder
		SqlElasticSearchRequestBuilder requestBuilder = queryAction.explain();
		// 之后就是对ES的操作
		SearchResponse response = (SearchResponse) requestBuilder.get();

		System.out.println("结果条数：" + response.getHits().getTotalHits());
		number = (int) response.getHits().getTotalHits();
		for (SearchHit hits : response.getHits()) {
			// System.out.println(hits.getSourceAsString());
			data.add(printJSON(hits.getSourceAsString()));
		}
		list.add(number);
		list.add(data);
		
		return list;

	}

	/**
	 * 根据传入的map，解析为sql语句进行查询emotion count
	 * 
	 * @param map
	 * @return
	 * @throws Exception
	 */
	public int searchEmotionCountBySql(Map<String, String> map, String type, Long count)
			throws Exception {
		/**1*/
		String cluesSql = "";
		String ids = "";
		//先查询map中有没有分类字段，有的话先查询出id集合
		String zeroIdStr = map.get("ZERO_ID");
		String firstIdStr = map.get("FIRST_CLASSIFY_ID");
		String secondIdStr = map.get("SECOND_CLASSIFY_ID");
		if (zeroIdStr != null && !"".equals(zeroIdStr)) {
			cluesSql = "select ID from tab_iopm_clues t where ZERO_ID=" + zeroIdStr;
			
			if (firstIdStr != null && !"".equals(firstIdStr)) {
				cluesSql += " and FIRST_ID=" + firstIdStr;
				
				if (secondIdStr != null && !"".equals(secondIdStr)) {
					cluesSql += " and SECOND_ID=" + secondIdStr;
				}
			}
		
			SQLExprParser parser2 = new ElasticSqlExprParser(cluesSql);
			SQLExpr expr2 = parser2.expr();
			if (parser2.getLexer().token() != Token.EOF) {
				throw new ParserException("illegal sql expr : " + cluesSql);
			}
			SQLQueryExpr queryExpr2 = (SQLQueryExpr) expr2;
			// 通过抽象语法树，封装成自定义的Select，包含了select、from、where group、limit等
			Select select2 = new SqlParser().parseSelect(queryExpr2);
	
			AggregationQueryAction action2;
			DefaultQueryAction queryAction2 = null;
			if (select2.isAgg) {
				// 包含计算的的排序分组的
				// request.setSearchType(SearchType.DEFAULT);
				action2 = new AggregationQueryAction(client, select2);
			} else {
				// 封装成自己的Select对象
				queryAction2 = new DefaultQueryAction(client, select2);
			}
			// 把属性封装在SearchRequestBuilder(client.prepareSearch()获取的即ES中获取的方法)对象中
			// 然后装饰了一下SearchRequestBuilder为自定义的SqlElasticSearchRequestBuilder
			SqlElasticSearchRequestBuilder requestBuilder2 = queryAction2.explain();
			// 之后就是对ES的操作
			SearchResponse response2 = (SearchResponse) requestBuilder2.get();
	
			System.out.println("结果条数：" + response2.getHits().getTotalHits());
			for (SearchHit hits : response2.getHits()) {
				// System.out.println(hits.getSourceAsString());
				ids +=printJSON2(hits.getSourceAsString());
				
				
			}
		}
		System.out.println(ids);
		if (!"".equals(ids)) {
//			String[] idsArr = ids.trim().split(",");
			map.put("IDSARR", ids);
		} else if (zeroIdStr != null && !"".equals(zeroIdStr)){
			map.put("IDSARR", "");
		}
		/**1 end*/
		
		/**2*/
		int number = 0;

		String sql = map2String2(map, type, count);
		
//		if (zeroIdStr != null && !"".equals(zeroIdStr) && "".equals(ids)){
//			sql += " and CLUES_IDS in ()";
//		}
		
		log.info("执行语句：" + sql);
		// 其中采用的是阿里的druid框架，
		// 其中ElasticLexer和ElasticSqlExprParser都是对druid中的MySql的进行了扩展
		SQLExprParser parser = new ElasticSqlExprParser(sql);
		SQLExpr expr = parser.expr();
		if (parser.getLexer().token() != Token.EOF) {
			throw new ParserException("illegal sql expr : " + sql);
		}
		SQLQueryExpr queryExpr = (SQLQueryExpr) expr;
		// 通过抽象语法树，封装成自定义的Select，包含了select、from、where group、limit等
		Select select = new SqlParser().parseSelect(queryExpr);

		AggregationQueryAction action = null;
		DefaultQueryAction queryAction = null;
		SqlElasticSearchRequestBuilder requestBuilder = null;
		if (select.isAgg) {
			// 包含计算的的排序分组的
			// request.setSearchType(SearchType.DEFAULT);
			action = new AggregationQueryAction(client, select);
			requestBuilder = action.explain();
		} else {
			// 封装成自己的Select对象
			queryAction = new DefaultQueryAction(client, select);
			requestBuilder = queryAction.explain();
		}
		// 把属性封装在SearchRequestBuilder(client.prepareSearch()获取的即ES中获取的方法)对象中
		// 然后装饰了一下SearchRequestBuilder为自定义的SqlElasticSearchRequestBuilder

		// 之后就是对ES的操作
		SearchResponse response = (SearchResponse) requestBuilder.get();

		System.out.println("结果条数：" + response.getHits().getTotalHits());
		number = (int) response.getHits().getTotalHits();

		return number;

	}

	public List<AtCount> searchEmotionCount(Map<String, String> map)
			throws Exception {
		List<AtCount> data = new ArrayList<AtCount>();
		AtCount atCount = null;
		int number = 0;
		String type = "";
		String value = "";
		//先根据whichChart的值进行分类
		if (map.get("whichChart") != null) {
			int whichChart = Integer.parseInt(map.get("whichChart"));
			switch (whichChart) {
			case 1:
				type = "INFO_TYPE";
				break;
			case 2:
				type = "EMOTION";
				break;
			case 3:
				type = "ZERO_ID";
				break;
			case 4:
				type = "FIRST_ID";
				break;
			case 5:
				type = "SECOND_ID";
				break;

			default:
				break;
			}
			//根据type为饼图查找值
			value = map.get(type);
			if (value != null && !"".equals(value)) {
				String[] str = value.trim().split(",");
				for (int i = 0; i < str.length; i++) {
					atCount = new AtCount();
					number = searchEmotionCountBySql(map,type,Long.parseLong(str[i]));
					atCount = getInfoByJson2(atCount, type, Long.parseLong(str[i]), (long)number);
					data.add(atCount);
				}
			}
		}

		return data;
	}

	/**
	 * 将map中的值转换为string
	 * 
	 * @param map
	 * @return
	 * @throws Exception
	 */
	public String map2String(Map<String, String> map) throws Exception {
		// RECORD_TIME RELEASE_TIME KEYWORDS SPREAD EMOTION CLUS_IDS INFO_TYPE
		// SORT SECOND_CLASSIFY_ID
		// FIRST_CLASSIFY_ID ZERO_ID HOT_ID
		// SORT SECOND_CLASSIFY_ID
		// FIRST_CLASSIFY_ID ZERO_ID
		String sql = "select * from tab_iopm_article_info where ID is not null ";
		String str = "";
		String startTime = "";
		String endTime = "";
		Set<String> set = map.keySet();
		Iterator<String> iter = set.iterator();
		while (iter.hasNext()) {
			String key = iter.next();
			String value = map.get(key);
			if ("RELEASE_TIME".equals(key)) {
				startTime = value;
			} else if ("RECORD_TIME".equals(key)) {
				endTime = value;
			} else if ("INFO_TYPE".equals(key)) {
				value = value.substring(0, value.length() - 1);
				sql += " and INFO_TYPE in (" + value + ")";
			} else if ("CLUS_IDS".equals(key)) {
				sql += " and ID=" + value;
			} else if ("HOT_ID".equals(key)) {
				sql += " and HOT_ID=" + value;
			} else if ("EMOTION".equals(key)) {
				value = value.substring(0, value.length() - 1);
				sql += " and EMOTION in (" + value + ")";
			} else if ("SPREAD".equals(key)) {
				sql += " and IS_VIP=" + value;
			} else if ("KEYWORDS".equals(key)) {
				String[] keywordsArray = value.trim().split(" ");
				if (keywordsArray.length > 0) {
					String strKeys = "";
					strKeys = " and ( (";
					for (int i = 0; i < keywordsArray.length; i++) {
						keywordsArray[i] = keywordsArray[i].trim();
						if (keywordsArray[i].length() > 0){
							if (i == 0) {
								strKeys += "TITLE = matchPhrase('"
										+ keywordsArray[i] + "')";
							} else {
								strKeys += " and TITLE = matchPhrase('"
										+ keywordsArray[i] + "')";
							}
						}
					}
					strKeys += " ) or (";
					for (int i = 0; i < keywordsArray.length; i++) {
						keywordsArray[i] = keywordsArray[i].trim();
						if (keywordsArray[i].length() > 0){
							if (i == 0) {
								strKeys += "CONTENT = matchPhrase('"
										+ keywordsArray[i] + "')";
							} else {
								strKeys += " and CONTENT = matchPhrase('"
										+ keywordsArray[i] + "')";
							}
						}
					}
					strKeys += " ) )";
					sql += strKeys;
				}
				// sql += " and TITLE = matchQuery('" + value
				// + "') and CONTENT = matchQuery('" + value + "')";
			} else if ("SORT".equals(key)) {
				if ("0".equals(value)) {
					str = " order by WEIGHT desc,RELEASE_TIME desc";
				} else if ("1".equals(value)) {
					str = " order by RELEASE_TIME desc";
				} else if ("2".equals(value)) {
					str = " order by COMMENT_COUNT desc";
				}
			} else if ("IDSARR".equals(key)) {
				if (value != null && !"".equals(value)) {
					if (',' == value.charAt(value.length()-1)) {
						value = value.substring(0,value.length()-1); //1,2,3,4 -> and CLUES_IDS like '%1%' or CLUES_IDS like '%2%'  
					}
					
					String cluesCondition = "";
					String []cluesIds = value.split(",");
					for (int i = 0; i < cluesIds.length; i++) {
						if (i == 0){
							cluesCondition += " and (CLUES_IDS like '%" + cluesIds[i] + "%'";
						}else{
							cluesCondition += " or CLUES_IDS like '%" + cluesIds[i] + "%'";
						}
					}
					if (!cluesCondition.equals("")){
						sql += cluesCondition + ")";
					}
				} 	
			}
		}
		
		sql += " and RELEASE_TIME >= '"+ startTime + " 00:00:00' and RELEASE_TIME <= '" + endTime + " 23:59:59'";
//		List<String> list = getTime(startTime, endTime);
//		if (list.size() > 0) {
//			String sql2 = " and (";
//			for (int i = 0; i < list.size(); i++) {
//				String string = list.get(i);
//				if (i == 0) {
//					sql2 += "RELEASE_TIME = '" + string + "'";
////							+ " or RECORD_TIME = '" + string + "'";
//				} else {
//					sql2 += " or RELEASE_TIME = '" + string + "'";
////							+ " or RECORD_TIME = '" + string + "'";
//				}
//			}
//			sql2 += ")";
//			sql += sql2;
//		}
		sql += str;
		System.out.println(sql);
		

		return sql;
	}
	
	
	/**
	 * 将map中的值转换为string  atcount
	 * 
	 * @param map
	 * @return
	 * @throws Exception
	 */
	public String map2String2(Map<String, String> map, String type, Long count) throws Exception {
		String sql = "select count(*) from tab_iopm_article_info where ID is not null ";
		String str = "";
		String startTime = "";
		String endTime = "";
		Set<String> set = map.keySet();
		Iterator<String> iter = set.iterator();
		while (iter.hasNext()) {
			String key = iter.next();
			String value = map.get(key);
			
			if ("RELEASE_TIME".equals(key)) {
				startTime = value;
			} else if ("RECORD_TIME".equals(key)) {
				endTime = value;
			} else if ("INFO_TYPE".equals(key)) {
				value = value.substring(0, value.length() - 1);
				sql += " and INFO_TYPE in (" + value + ")";
			} else if ("CLUS_IDS".equals(key)) {
				sql += " and ID=" + value;
			} else if ("HOT_ID".equals(key)) {
				sql += " and HOT_ID=" + value;
			} else if ("EMOTION".equals(key)) {
				value = value.substring(0, value.length() - 1);
				sql += " and EMOTION in (" + value + ")";
			} else if ("SPREAD".equals(key)) {
				sql += " and IS_VIP=" + value;
			} else if ("KEYWORDS".equals(key)) {
				String[] keywordsArray = value.trim().split(" ");
				if (keywordsArray.length > 0) {
					String strKeys = "";
					strKeys = " and ( (";
					for (int i = 0; i < keywordsArray.length; i++) {
						if (i == 0) {
							strKeys += "TITLE = matchPhrase('"
									+ keywordsArray[i] + "')";
						} else {
							strKeys += " and TITLE = matchPhrase('"
									+ keywordsArray[i] + "')";
						}
					}
					strKeys += " ) or (";
					for (int i = 0; i < keywordsArray.length; i++) {
						if (i == 0) {
							strKeys += "CONTENT = matchPhrase('"
									+ keywordsArray[i] + "')";
						} else {
							strKeys += " and CONTENT = matchPhrase('"
									+ keywordsArray[i] + "')";
						}
					}
					strKeys += " ) )";
					sql += strKeys;
				}
				// sql += " and TITLE = matchQuery('" + value
				// + "') and CONTENT = matchQuery('" + value + "')";
				
				//zero_id  first_classify_id  second_classify_id  whichChart   

				
			} else if ("IDSARR".equals(key)) {
				
				if (value != null && !"".equals(value)) {
					String cluesCondition = "";
					String []cluesIds = value.split(",");
					for (int i = 0; i < cluesIds.length; i++) {
						if (i == 0){
							cluesCondition += " and (CLUES_IDS like '%" + cluesIds[i] + "%'";
						}else{
							cluesCondition += " or CLUES_IDS like '%" + cluesIds[i] + "%'";
						}
					}
					
					if (!cluesCondition.equals("")){
						sql += cluesCondition + ")";
					}
					
				}
			} 
		}
		sql += " and RELEASE_TIME >= '"+ startTime + " 00:00:00' and RELEASE_TIME <= '" + endTime + " 23:59:59'";
//		List<String> list = getTime(startTime, endTime);
//		if (list.size() > 0) {
//			String sql2 = " and (";
//			for (int i = 0; i < list.size(); i++) {
//				String string = list.get(i);
//				if (i == 0) {
//					sql2 += "RELEASE_TIME = '" + string + "'";
////							+ " or RECORD_TIME = '" + string + "'";
//				} else {
//					sql2 += " or RELEASE_TIME = '" + string + "'";
////							+ " or RECORD_TIME = '" + string + "'";
//				}
//			}
//			sql2 += ")";
//			sql += sql2;
//		}
		sql += str;
		
		
		if ("INFO_TYPE".equals(type) && count != null) {
			sql += " and INFO_TYPE = " + count;
		} else if ("EMOTION".equals(type) && count != null) {
			sql += " and EMOTION = " + count;
		} else {
			
		}

		return sql;
	}

	/**
	 * 将字符串转换为实体类
	 * 
	 * @param str
	 * @return
	 */
	public ArticleInfo printJSON(String str) {
		ArticleInfo info = new ArticleInfo();
		JSONArray arry = JSONArray.fromObject("[" + str + "]");
		for (int i = 0; i < arry.size(); i++) {
			JSONObject jsonObject = arry.getJSONObject(i);
			// Map<String, String> map = new HashMap<String, String>();
			for (Iterator<?> iter = jsonObject.keys(); iter.hasNext();) {
				// 将查到的值封装到实体中，并根据未传入的条件，如时间等再对现有结果集进行二次查询。//
				String key = (String) iter.next();
				String value = jsonObject.get(key).toString();
//				System.out.println(key + ": " + value);
				info = getInfoByJson(info, key, value);
			}
//			System.out.println("*****************************************");
		}
		return info;
	}
	
	/**
	 * 将字符串转换为实体类
	 * 
	 * @param str
	 * @return
	 */
	public String printJSON2(String str) {
		String strR = "";
		ArticleInfo info = new ArticleInfo();
		JSONArray arry = JSONArray.fromObject("[" + str + "]");
		for (int i = 0; i < arry.size(); i++) {
			JSONObject jsonObject = arry.getJSONObject(i);
			// Map<String, String> map = new HashMap<String, String>();
			for (Iterator<?> iter = jsonObject.keys(); iter.hasNext();) {
				// 将查到的值封装到实体中，并根据未传入的条件，如时间等再对现有结果集进行二次查询。//
				String key = (String) iter.next();
				if (key != null && "ID".equals(key)) {
					strR += jsonObject.get(key).toString() + ",";
				}
//				String value = jsonObject.get(key).toString();
//				System.out.println(key + ": " + value);
//				info = getInfoByJson(info, key, value);
			}
//			System.out.println("*****************************************");
		}
		return strR;
	}


	/**
	 * 为实体类赋值
	 * 
	 * @param info
	 * @param key
	 * @param value
	 * @return
	 */
	public ArticleInfo getInfoByJson(ArticleInfo info, String key, String value) {
		/** IMAGE_URL */
		// 15
		if ("KEYWORDS".equals(key)) {
			if (!"null".equals(value)) {
				info.setKeywords(value);
			}
		} else if ("TITLE".equals(key)) {
			if (!"null".equals(value)) {
				info.setTitle(value);
			}
		} else if ("CONTENT".equals(key)) {
			if (!"null".equals(value)) {
				info.setContent(value);
			}
		} else if ("URL".equals(key)) {
			if (!"null".equals(value)) {
				info.setUrl(value);
			}
		} else if ("WEBSITE_NAME".equals(key)) {
			if (!"null".equals(value)) {
				info.setWebsiteName(value);
			}
		} else if ("EMOTION".equals(key)) {
			if (!"null".equals(value)) {
				info.setEmotion(Long.parseLong(value));
			}
		} else if ("REVIEW_COUNT".equals(key)) {
			if (!"null".equals(value)) {
				info.setReviewCount(Long.parseLong(value));
			}
		} else if ("COMMENT_COUNT".equals(key)) {
			if (!"null".equals(value)) {
				info.setCommtcount(Long.parseLong(value));
			}
		} else if ("AUTHOR".equals(key)) {
			if (!"null".equals(value)) {
				info.setAuthor(value);
			}
		} else if ("UP_COUNT".equals(key)) {
			if (!"null".equals(value)) {
				info.setUpCount(Long.parseLong(value));
			}
		} else if ("TRANSMIT_COUNT".equals(key)) {
			if (!"null".equals(value)) {
				info.setTransmitCount(Long.parseLong(value));
			}
		} else if ("ID".equals(key)) {
			if (!"null".equals(value)) {
				info.setId(Long.parseLong(value));
			}
		} else if ("INFO_TYPE".equals(key)) {
			if (!"null".equals(value)) {
				info.setType(Long.parseLong(value));
			}
		} else if ("RELEASE_TIME".equals(key)) {
			if (!"null".equals(value)) {
				info.setReleaseTime(value);
			}
		} else if ("RECORD_TIME".equals(key)) {
			if (!"null".equals(value)) {
				info.setRecordTime(value);
			}
		} else {

		}
		return info;
	}

	/**
	 * 为实体类赋值AtCount
	 * 
	 * @param info
	 * @param key
	 * @param value
	 * @return
	 */
	public AtCount getInfoByJson2(AtCount info, String classify, Long type, Long number) {
		if ("EMOTION".equals(classify)) {
				info.setEmotion(type);
				info.setCount(number);	
		} else if ("FIRST_ID".equals(classify)) {
				info.setFirst_id(type);
				info.setCount(number);	
		} else if ("INFO_TYPE".equals(classify)) {
				info.setInfo_type(type);
				info.setCount(number);	
		} else if ("SECOND_ID".equals(type)) {
				info.setSecond_id(type);
				info.setCount(number);	
		} else if ("ZERO_ID".equals(type)) {
				info.setZero_id(type);
				info.setCount(number);	
		} else {

		}
		return info;
	}

	public static int compare_date(String DATE1, String DATE2) {

		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		try {
			Date dt1 = df.parse(DATE1);
			Date dt2 = df.parse(DATE2);
			if (dt1.getTime() > dt2.getTime()) {
				System.out.println("dt1 在dt2前");
				return 1;
			} else if (dt1.getTime() < dt2.getTime()) {
				System.out.println("dt1在dt2后");
				return -1;
			} else {
				return 0;
			}
		} catch (Exception exception) {
			exception.printStackTrace();
		}
		return 0;
	}

	public List<String> getTime(String startTime, String endTime)
			throws Exception {
		List<String> list = new ArrayList<String>();
		// list.add(startTime);
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		Date start = format.parse(startTime);
		Date end = format.parse(endTime);
		int i = (int) ((end.getTime() - start.getTime()) / (1000 * 3600 * 24));
		for (int j = 0; j <= i; j++) {
			String str = format.format(new Date(end.getTime() - 24 * 3600000
					* j));
			list.add(str);
		}
		return list;
	}
	
	public List<Object> searchBySql(String sql) throws SqlParseException{
		List<Object> list = new ArrayList<Object>();
		List<Object> data = new ArrayList<Object>();
		int number = 0;


		// 其中采用的是阿里的druid框架，
		// 其中ElasticLexer和ElasticSqlExprParser都是对druid中的MySql的进行了扩展
		SQLExprParser parser = new ElasticSqlExprParser(sql);
		SQLExpr expr = parser.expr();
		if (parser.getLexer().token() != Token.EOF) {
			throw new ParserException("illegal sql expr : " + sql);
		}
		SQLQueryExpr queryExpr = (SQLQueryExpr) expr;
		// 通过抽象语法树，封装成自定义的Select，包含了select、from、where group、limit等
		Select select = new SqlParser().parseSelect(queryExpr);

		AggregationQueryAction action;
		DefaultQueryAction queryAction = null;
		SqlElasticSearchRequestBuilder requestBuilder = null;
		if (select.isAgg) {
			// 包含计算的的排序分组的
			// request.setSearchType(SearchType.DEFAULT);
			action = new AggregationQueryAction(client, select);
			requestBuilder = action.explain();
		} else {
			// 封装成自己的Select对象
			queryAction = new DefaultQueryAction(client, select);
			requestBuilder = queryAction.explain();
		}
		// 把属性封装在SearchRequestBuilder(client.prepareSearch()获取的即ES中获取的方法)对象中
		// 然后装饰了一下SearchRequestBuilder为自定义的SqlElasticSearchRequestBuilder
		 
		// 之后就是对ES的操作
		SearchResponse response = (SearchResponse) requestBuilder.get();

		System.out.println("结果条数：" + response.getHits().getTotalHits());
		number = (int) response.getHits().getTotalHits();
		
		for (SearchHit hits : response.getHits()) {
//			 System.out.println(hits.getSourceAsString());
//			data.add(printJSON(hits.getSourceAsString()));
			 data.add(hits.getSourceAsString());
		}
		list.add(number);
		list.add(data);
		
		return list;

	}

	public static void main(String[] args) throws Exception {
		ESSearchUtils es = ESSearchUtils.getInstance();

		String sql = "SELECT * FROM tab_iopm_article_info where (TITLE = '梁宏达' or CONTENT = '梁宏达') and (TITLE = '雷锋' or CONTENT = '雷锋')";
		String s = "2017-07-23";
		//select RELEASE_TIME from tab_iopm_article_info where  (RELEASE_TIME >= '2017-10-01 00:00:00' and RELEASE_TIME <= '2017-10-01 23:59:59')and (TITLE like '%焦点访谈%' OR CONTENT LIKE'%焦点访谈%') and IS_VIP=1
		List<Object> result = es.searchBySql(sql);
		System.out.println(result);
	}
}
