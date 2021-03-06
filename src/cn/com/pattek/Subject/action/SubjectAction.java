package cn.com.pattek.Subject.action;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileOutputStream;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.collections.map.HashedMap;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.struts2.ServletActionContext;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STVerticalJc;
import org.springframework.beans.factory.annotation.Autowired;

import cn.com.pattek.Subject.dao.SubjectDao;
import cn.com.pattek.Subject.entity.TabIopmSubject;
import cn.com.pattek.core.struts2.BaseAction;
import cn.com.pattek.core.struts2.JsonUtils;
import cn.com.pattek.utils.CustomXWPFDocument;
import cn.com.pattek.utils.ESSearchUtils;
import cn.com.pattek.utils.FileUtils;
import cn.com.pattek.utils.HttpRequest;
import cn.com.pattek.utils.POIWordUtils;
import cn.com.pattek.utils.PropertiesFactory;
import cn.com.pattek.utils.Tools;


public class SubjectAction extends BaseAction{


	@Autowired
	private SubjectDao subjectDao;

	/** 前台传来的专题id**/
	private static Long id ;
	private static Long subjectId = null;
	/** 专题名称 **/
	private String name;
	/** 开始时间 **/
	private static String startTime;
	/** 结束时间 **/
	private static String endTime;
	/** 包含的关键词 （空格表示与， 逗号表示或） **/
	private static String keyword1;
	/** 不包含的关键词（空格表示与 ，逗号表示或） **/
	private static String keyword2;
	/** 修改时间 **/
	private static String updateTime;  
    /**关键词创建时间*/
    private static String recordTime;   
    /**搜索专题-字段*/
    private String nameKeyWords;  

	private static String subjectName;//前台接受的专题名字
	
	private static String picture;//前台传来用与生成word的图片数据
	private  static String text;//前台传来用与生成word的text数据
	
	private static int type;//1-生成word 2-推送微信
	

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public String getPicture() {
		return picture;
	}

	public void setPicture(String picture) {
		this.picture = picture;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}
	

	public void loadSubjectMessage() throws Exception{
		if (subjectId != id){
			keyword1 = subjectDao.subjectKeyWord1(id);
			startTime = subjectDao.subjectStart(id);
			endTime = subjectDao.subjectEndTime(id);
			Date date= new Date();
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			Date endDate = format.parse(endTime);
			//System.out.println(date);
			int compareTo = date.compareTo(endDate);
			if(compareTo<0){
				endDate=date;
				String d2S = subjectDao.date2String(endDate);
				SimpleDateFormat sDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				String f = sDF.format(date);
				endTime =f;
				//System.out.println(endTime);
			}
			subjectId = id;
		}
	}
	
	public String cutContent(String content) throws Exception{
	
		return Tools.cutText(content.replace("\r","").replace("\n", ""), 50);
	}


	/**
	 *分页信息
	 */
	private int page ;//接受的页码
	private int size ;//每页的数量
	public int getPage()
	{
		return page;
	}
	public void setPage(int page)
	{
		this.page = page;
	}
	public int getSize()
	{
		return size;
	}
	public void setSize(int size)
	{
		this.size = size;
	}
	
	public String saveData() throws Exception{
		HttpServletResponse response = ServletActionContext.getResponse();
		response.setContentType("text/html;charset=UTF-8");// 解决中文乱码
		response.getWriter().write("/IOPM/Subject/SubjectAction_wordDownload.action");
		return null;
	}
	
	public String wordDownload() throws Exception{
		try {
			HttpSession session = ServletActionContext.getRequest().getSession();	
			JSONObject pictureData = JSONObject.fromObject(picture);
			JSONObject textData = JSONObject.fromObject(text);
			String titleName = subjectDao.selectTitle(id);

			CustomXWPFDocument document = new CustomXWPFDocument();//创建word对象
			XWPFParagraph p1 = document.createParagraph();//创建一个自然段
			p1.setAlignment(ParagraphAlignment.CENTER);//居中
			XWPFRun r1 = p1.createRun();
			r1.setText(titleName);
			r1.setBold(true);//加粗
			r1.setFontSize(22);
			creatBlank(document);
			
			creatlitleTitle(document, "一、舆情概述");
			
			String yuqinggaishu = textData.get("summary1").toString();//舆情概述正文
			straightMatter(document, yuqinggaishu);
			String summary2 = textData.get("summary2").toString();
			 straightMatter(document, summary2);
			 creatBlank(document);
			creatlitleTitle(document, "二、舆情统计");
			String yuqing = textData.get("yuqing").toString();
			 straightMatter(document, yuqing);
			
			creatPicture(pictureData, "one", "../webapps/IOPM/subject/picture/picture.jpg", 640, 210, document);
			 
			XWPFTable p51 = document.createTable(6, 4);//创建一个6行4列的表格
			p51.getRow(0).getCell(0).setText("排名");
			p51.getRow(0).getCell(1).setText("线索类别");
			p51.getRow(0).getCell(2).setText("信息总量");
			p51.getRow(0).getCell(3).setText("负面信息量");
			//拿到所需要的每一个cell
			POIWordUtils.setCellWidthAndVAlign(p51.getRow(0).getCell(0), "1720", STTblWidth.DXA, STVerticalJc.TOP);
			POIWordUtils.setCellWidthAndVAlign(p51.getRow(0).getCell(1), "2270", STTblWidth.DXA, STVerticalJc.TOP);
			POIWordUtils.setCellWidthAndVAlign(p51.getRow(0).getCell(2), "2270", STTblWidth.DXA, STVerticalJc.TOP);
			POIWordUtils.setCellWidthAndVAlign(p51.getRow(0).getCell(3), "2270", STTblWidth.DXA, STVerticalJc.TOP);
			//POIWordUtils.setCellWidthAndVAlign(cell, width, typeEnum, vAlign);
			Map<Object, Object> ClassifyMsgCount = (Map<Object, Object>) session.getAttribute("ClassifyMsgCount");//在session中获取数据
			ArrayList<Map<Object, Object>> totalSort = (ArrayList<Map<Object, Object>>) ClassifyMsgCount.get("totalSort");
			//循环得出数据填充到表格中
			for (Integer i = 0; i < totalSort.size(); i++) {					
				
												
					POIWordUtils.setCellWidthAndVAlign(p51.getRow(i+1).getCell(0), "1720", STTblWidth.DXA, STVerticalJc.TOP);
					POIWordUtils.setCellWidthAndVAlign(p51.getRow(i+1).getCell(1), "2270", STTblWidth.DXA, STVerticalJc.TOP);
					POIWordUtils.setCellWidthAndVAlign(p51.getRow(i+1).getCell(2), "2270", STTblWidth.DXA, STVerticalJc.TOP);
					POIWordUtils.setCellWidthAndVAlign(p51.getRow(i+1).getCell(3), "2270", STTblWidth.DXA, STVerticalJc.TOP);
					
					String name = totalSort.get(i).get("name").toString();//线索类别
					String totalMsg = totalSort.get(i).get("totalMsg").toString();//信息总量
					String negativeEmotionCount = totalSort.get(i).get("negativeEmotionCount").toString();//负面信息量
									
					p51.getRow(i+1).getCell(0).setText(Integer.valueOf(i+1).toString());
					p51.getRow(i+1).getCell(1).setText(name);
					p51.getRow(i+1).getCell(2).setText(totalMsg);
					p51.getRow(i+1).getCell(3).setText(negativeEmotionCount);		
			}
			
			 
			 creatBlank(document);
			 creatlitleTitle(document, "三、事件综合影响力分析");

			 /*creatPicture(pictureData, "two","../webapps/IOPM/subject/picture/zongheyingxiangli.jpg", 600, 150, document);
			 creatPicture(pictureData, "three","../webapps/IOPM/subject/picture/zongheyingxiangli1.jpg", 600, 150, document);*/
			 creatTwoPicture(pictureData, "two", "three", "../webapps/IOPM/subject/picture/picture.jpg", "../webapps/IOPM/subject/picture/picture2.jpg", 205, 100, 205, 100, document);
			 

			 String effect = textData.get("effect").toString();//舆情概述
			 straightMatter(document, effect);
			 creatBlank(document);
			 straightMatter(document, "注：① 事件影响力评分根据新闻报道指数、微博扩散指数、论坛参与指数、以及境外炒作指数等多项指标综合计算得出。评分范围为0-100，其中0-20、21-40、41-60、61-80、81-100区间对应影响力分别为较小、一般、较大、很大、极大。数值越大，表明在监测周期内分析对象的传播影响力越大。");
			 
			 creatBlank(document);
			 creatlitleTitle(document, "四、事件阶段演化分析");
			 
			 creatPicture(pictureData, "four","../webapps/IOPM/subject/picture/picture.jpg" , 610, 200, document);
			 String stage = textData.get("stage").toString();
			 straightMatter(document, stage);
						 
			 creatBlank(document);
			 creatlitleTitle(document, "五、网上传播情况分析");
			 
			 creatTitlesecond(document, "1、主流媒体传播趋势");
			 
			 creatPicture(pictureData, "five", "../webapps/IOPM/subject/picture/picture.jpg", 630, 200, document);
			 
			 creatTableNameAndBlank(document, "负面信息量");
			 //获得主流媒体传播趋势的排名数据
			 HashedMap VIPAndBadEmotionAndText = (HashedMap) session.getAttribute("VIPAndBadEmotionAndText");
			 ArrayList<Object> VIP = (ArrayList<Object>) VIPAndBadEmotionAndText.get("VIP");
			  
			 XWPFTable VIPTable = document.createTable(VIP.size()+1, 3);
			 VIPTable.getRow(0).getCell(0).setText("排名");
			 VIPTable.getRow(0).getCell(1).setText("网站");
			 VIPTable.getRow(0).getCell(2).setText("负面信息量");
			 POIWordUtils.setCellWidthAndVAlign(VIPTable.getRow(0).getCell(0), "2580", STTblWidth.DXA, STVerticalJc.TOP);
			 POIWordUtils.setCellWidthAndVAlign(VIPTable.getRow(0).getCell(1), "3010", STTblWidth.DXA, STVerticalJc.TOP);
			 POIWordUtils.setCellWidthAndVAlign(VIPTable.getRow(0).getCell(2), "3010", STTblWidth.DXA, STVerticalJc.TOP);
			 for (int i = 0; i < VIP.size(); i++) {
				HashedMap oneData = (HashedMap) VIP.get(i);
				String websit_name = oneData.get("WEBSIT_NAME").toString();
				String info_count = oneData.get("INFO_COUNT").toString();
				POIWordUtils.setCellWidthAndVAlign(VIPTable.getRow(i+1).getCell(0), "2580", STTblWidth.DXA, STVerticalJc.TOP);
				POIWordUtils.setCellWidthAndVAlign(VIPTable.getRow(i+1).getCell(1), "3010", STTblWidth.DXA, STVerticalJc.TOP);
				POIWordUtils.setCellWidthAndVAlign(VIPTable.getRow(i+1).getCell(2), "3010", STTblWidth.DXA, STVerticalJc.TOP);
				VIPTable.getRow(i+1).getCell(0).setText(Integer.valueOf(i+1).toString());
				VIPTable.getRow(i+1).getCell(1).setText(websit_name);
				VIPTable.getRow(i+1).getCell(2).setText(info_count);	 
			 }
			 
			 creatTableNameAndBlank(document, "信息总量");
			
			 ArrayList<Object> VIPAndbadEmotion = (ArrayList<Object>) VIPAndBadEmotionAndText.get("VIPAndbadEmotion");
			 
			 XWPFTable VIPAndbadEmotionTable = document.createTable(VIP.size()+1, 3);
			 VIPAndbadEmotionTable.getRow(0).getCell(0).setText("排名");
			 VIPAndbadEmotionTable.getRow(0).getCell(1).setText("网站");
			 VIPAndbadEmotionTable.getRow(0).getCell(2).setText("总信息量");
			 POIWordUtils.setCellWidthAndVAlign(VIPAndbadEmotionTable.getRow(0).getCell(0), "2580", STTblWidth.DXA, STVerticalJc.TOP);
			 POIWordUtils.setCellWidthAndVAlign(VIPAndbadEmotionTable.getRow(0).getCell(1), "3010", STTblWidth.DXA, STVerticalJc.TOP);
			 POIWordUtils.setCellWidthAndVAlign(VIPAndbadEmotionTable.getRow(0).getCell(2), "3010", STTblWidth.DXA, STVerticalJc.TOP);
			 for (int i = 0; i < VIP.size(); i++) {
				 HashedMap oneData = (HashedMap) VIP.get(i);
				 String websit_name = oneData.get("WEBSIT_NAME").toString();
				 String info_count = oneData.get("INFO_COUNT").toString();
				 POIWordUtils.setCellWidthAndVAlign(VIPAndbadEmotionTable.getRow(i+1).getCell(0), "2580", STTblWidth.DXA, STVerticalJc.TOP);
				 POIWordUtils.setCellWidthAndVAlign(VIPAndbadEmotionTable.getRow(i+1).getCell(1), "3010", STTblWidth.DXA, STVerticalJc.TOP);
				 POIWordUtils.setCellWidthAndVAlign(VIPAndbadEmotionTable.getRow(i+1).getCell(2), "3010", STTblWidth.DXA, STVerticalJc.TOP);
				 VIPAndbadEmotionTable.getRow(i+1).getCell(0).setText(Integer.valueOf(i+1).toString());
				 VIPAndbadEmotionTable.getRow(i+1).getCell(1).setText(websit_name);
				 VIPAndbadEmotionTable.getRow(i+1).getCell(2).setText(info_count);	 
			 }	
			 
			 String spread_news = textData.get("spread_news").toString();
			 straightMatter(document, spread_news);
			 
			 creatBlank(document);
			 creatTitlesecond(document,"2、微博平台传播趋势");
			 
			 creatPicture(pictureData, "six", "../webapps/IOPM/subject/picture/picture.jpg", 620, 250, document);
			 
			 creatTableNameAndBlank(document, "发现最早微博");
			 creat_WeiboAndWeixinTable(session, "weiBoRank", document, "early");		 
			 creatTableNameAndBlank(document, "点赞最多微博");
			 creat_WeiboAndWeixinTable(session, "weiBoRank", document, "upMax");
			 creatTableNameAndBlank(document, "转发最多微博");
			 creat_WeiboAndWeixinTable(session, "weiBoRank", document, "transmit");		 
			 creatTableNameAndBlank(document, "评论最多微博");
			 creat_WeiboAndWeixinTable(session, "weiBoRank", document, "comment");
			
			 creatBlank(document);//创建一个空行
			 String spread_mblog = textData.get("spread_mblog").toString();
			 straightMatter(document, spread_mblog);
			 
			 creatBlank(document);//创建一个空行
			 creatTitlesecond(document, "3、微信平台传播趋势");
			 creatPicture(pictureData, "seven", "../webapps/IOPM/subject/picture/picture.jpg", 620, 250, document);
			 creatTableNameAndBlank(document, "发现最早微信");
			 creat_WeiboAndWeixinTable(session, "weiXinRank", document, "early");
			 creatTableNameAndBlank(document, "点赞最多微信");
			 creat_WeiboAndWeixinTable(session, "weiXinRank", document, "upMax");
			 creatTableNameAndBlank(document, "阅读最多微信");
			 creat_WeiboAndWeixinTable(session, "weiXinRank", document, "comment");		
			 creatBlank(document);//创建一个空行
			 
			 String spread_wechat = textData.get("spread_wechat").toString();
			 straightMatter(document, spread_wechat);
			 creatBlank(document);//创建一个空行
			 
			 creatlitleTitle(document, "六、事件溯源分析");
			 
			 //少一个表格
			 Map<Object, Object> attribute = (Map<Object, Object>) session.getAttribute("suyuan");
			 ArrayList<Map<Object, Object>> releaseTimePaiXuAll =(ArrayList<Map<Object, Object>>) attribute.get("releaseTimePaiXu");
			 XWPFTable suyuanTable = document.createTable(releaseTimePaiXuAll.size()+1, 5);//创建一个的表格
			 suyuanTable.getRow(0).getCell(0).setText("类型");
			 suyuanTable.getRow(0).getCell(1).setText("站点");
			 suyuanTable.getRow(0).getCell(2).setText("标题");
			 suyuanTable.getRow(0).getCell(3).setText("发布时间");
			 suyuanTable.getRow(0).getCell(4).setText("URL");
			 POIWordUtils.setCellWidthAndVAlign(suyuanTable.getRow(0).getCell(0), "1720", STTblWidth.DXA, STVerticalJc.TOP);
			 POIWordUtils.setCellWidthAndVAlign(suyuanTable.getRow(0).getCell(1), "1775", STTblWidth.DXA, STVerticalJc.TOP);
			 POIWordUtils.setCellWidthAndVAlign(suyuanTable.getRow(0).getCell(2), "1775", STTblWidth.DXA, STVerticalJc.TOP);
			 POIWordUtils.setCellWidthAndVAlign(suyuanTable.getRow(0).getCell(3), "1775", STTblWidth.DXA, STVerticalJc.TOP);
			 POIWordUtils.setCellWidthAndVAlign(suyuanTable.getRow(0).getCell(4), "1775", STTblWidth.DXA, STVerticalJc.TOP);
			 for (int i = 0; i < releaseTimePaiXuAll.size(); i++) {
				 POIWordUtils.setCellWidthAndVAlign(suyuanTable.getRow(i+1).getCell(0), "1720", STTblWidth.DXA, STVerticalJc.TOP);
				 POIWordUtils.setCellWidthAndVAlign(suyuanTable.getRow(i+1).getCell(1), "1775", STTblWidth.DXA, STVerticalJc.TOP);
				 POIWordUtils.setCellWidthAndVAlign(suyuanTable.getRow(i+1).getCell(2), "1775", STTblWidth.DXA, STVerticalJc.TOP);
				 POIWordUtils.setCellWidthAndVAlign(suyuanTable.getRow(i+1).getCell(3), "1775", STTblWidth.DXA, STVerticalJc.TOP);
				 POIWordUtils.setCellWidthAndVAlign(suyuanTable.getRow(i+1).getCell(4), "1775", STTblWidth.DXA, STVerticalJc.TOP);
			
				 String infoType = releaseTimePaiXuAll.get(i).get("infoType").toString();
				 String websiteName = releaseTimePaiXuAll.get(i).get("websiteName").toString();
				 String title = releaseTimePaiXuAll.get(i).get("title").toString();
				 String time = releaseTimePaiXuAll.get(i).get("time").toString();
				 String url = releaseTimePaiXuAll.get(i).get("url").toString();
				 Date date = new Date(Long.valueOf(time));
				 SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
				 String formatDate = format.format(date);
				 time = formatDate.toString();
				 int type = Integer.parseInt(infoType);
				 //信息类型  0全网 1，新闻；2，微信；3，微博；4，论坛；7，app；8，视频
				 if(type==0){
					 infoType ="全网";
				 }
				 if(type==1){
					 infoType ="新闻";
				 }
				 if(type==2){
					 infoType ="微信";
				 }
				 if(type==3){
					 infoType ="微博";
				 }
				 if(type==4){
					 infoType ="论坛";
				 }
				 if(type==7){
					 infoType ="app";
				 }
				 if(type==8){
					 infoType ="视频";
				 }
				 
				 
				 
				// p51.getRow(i+1).getCell(0).setText(Integer.valueOf(i+1).toString());
				 suyuanTable.getRow(i+1).getCell(0).setText(infoType);
				 suyuanTable.getRow(i+1).getCell(1).setText(websiteName);
				 suyuanTable.getRow(i+1).getCell(2).setText(title);	
				 suyuanTable.getRow(i+1).getCell(3).setText(time);	
				 suyuanTable.getRow(i+1).getCell(4).setText(url);	
			 
			 }
			 
			 
			 
			 
			 

			 
			 
			 
			 textData.get("trace").toString();
			 straightMatter(document, spread_wechat);
			 creatBlank(document);//创建一个空行
			
			 creatlitleTitle(document, "七、网民意见分析");
			 creatTitlesecond(document, "1、网民情感波动及倾向");
			 creatTwoPicture(pictureData, "eight", "nine", "../webapps/IOPM/subject/picture/picture.jpg", "../webapps/IOPM/subject/picture/picture2.jpg", 205, 100, 205, 100, document);
			 creatBlank(document);//创建一个空行
			 
			 String yijian = textData.get("yijian").toString();
			 straightMatter(document, yijian);
			 creatBlank(document);//创建一个空行
			 
			 creatTitlesecond(document, "2、网民意见分布");
			 
			 String positive = textData.get("positive").toString();
			 straightMatter(document, positive);
			 creatBlank(document);//创建一个空行
			 String negative = textData.get("negative").toString();
			 straightMatter(document, negative);
			 creatBlank(document);//创建一个空行
			 String neutral = textData.get("neutral").toString();
			 straightMatter(document, neutral);
			 creatBlank(document);//创建一个空行
			
			 creatlitleTitle(document, "八、综合研判");
			 String zonghe = textData.get("zonghe").toString();
			 straightMatter(document, zonghe);
			 straightMatter(document, "因此，该事件应引起相关单位的高度注意，并立即启动应急预案，积极处置事件相关事宜的同时，做好舆情监测及信息发布工作，积极回应舆论关切，舒缓、平息舆论质疑/继续密切关注舆论发展态势，做好舆论应对准备，并视舆情发展态势决定是否作出回应/继续关注事件舆情态势。");
			 Date date = new Date();
			 String filename="《"+titleName+"》专题报告.docx";
			 if(type==1){
			 //生成word
				 //获取浏览器类型
				 String agent = ServletActionContext.getRequest().getHeader("User-Agent");
				 String mimeType = ServletActionContext
						 .getServletContext().getMimeType(filename);
				 //根据浏览器类型对文件名编码
				 filename = FileUtils.encodeDownloadFilename(filename, agent);
				 // 4.1一个流：response的输出流
				 ServletOutputStream os = ServletActionContext
						 .getResponse().getOutputStream();
				 // 4.2两个头之一：content-type，告诉前台浏览器返回数据的格式：xml,css,html,json,xls等等
				 ServletActionContext.getResponse().setContentType(mimeType);
				 // 4.3两个头之二：content-disposition，告诉前台浏览器数据的打开方式，附件方式打开值如下：attachment;filename=文件名
				 ServletActionContext.getResponse().setHeader("content-disposition", "attachment;filename="+filename);
				 // 5.将excel通过response返回到前台
				 document.write(os);	}	 
			 
			 if(type==2){
				 FileOutputStream out = null;
				 
				
				String subjectFilePath = PropertiesFactory.getValue("tempFilePath") + filename;
				out = new FileOutputStream(subjectFilePath);
				document.write(out);
				out.flush();
				out.close();
				Map<Object, Object> map = new HashedMap();
				map.put("url",subjectFilePath);
				map.put("status", 1);
				objectToJson(map);
				subjectDao.updateIsSend(id);

			 }

		} catch (Exception e) {
			e.printStackTrace();
			Map<Object, Object> map = new HashedMap();
			map.put("url", "");
			map.put("status", 0);
			this.objectToJson(map);	    
		}
			

		
		return null;
	}

	private void creatTitlesecond(CustomXWPFDocument document,String title) {
		XWPFParagraph p15 = document.createParagraph();
		 XWPFRun r15 = p15.createRun();
		 r15.setText(title);//标题-标题
		 r15.setBold(true);//设置为粗体
	}

	private void creatlitleTitle(CustomXWPFDocument document,String title) {
		XWPFParagraph p2 = document.createParagraph();
		XWPFRun r2 = p2.createRun();
		r2.setText(title);//标题
		r2.setBold(true);//设置为粗体
		r2.setFontSize(16);//---字体大小
	}

	private void creatBlank(CustomXWPFDocument document) {
		XWPFParagraph b2 = document.createParagraph();//创建一个自然段
			XWPFRun br2 = b2.createRun();
			br2.setText("");
	}

	private void creatPicture(JSONObject pictureData,String dataName,String creatPath,int size1,int size2,
			CustomXWPFDocument document) throws InvalidFormatException,
			IOException, FileNotFoundException {
		XWPFParagraph p5 = document.createParagraph();
		XWPFRun r5 = p5.createRun();
		String onePicture = pictureData.get(dataName).toString();//转化为字符串数据
		String onePictureCode = onePicture.substring(22);//将前面没用的数据剔除
		String imgStr = onePictureCode;//获得完整64编码
		POIWordUtils.GenerateImage(imgStr, creatPath);//生成了图片
		//FileInputStream f = new FileInputStream("../webapps/IOPM/subject/picture/yuqingtongji.jpg");
		r5.addPicture(new FileInputStream(creatPath), Document.PICTURE_TYPE_JPEG, "", Units.toEMU(0), Units.toEMU(0));
		document.createPicture(p5, document.getAllPictures().size()-1, size1, size2,"");//生成图片
	}
	private void creatTwoPicture(JSONObject pictureData,String dataName1,String dataName2,String creatPath,String creatPath2,int size1,int size2,int size3,int size4,
			CustomXWPFDocument document) throws InvalidFormatException,
			IOException, FileNotFoundException {
		XWPFParagraph p5 = document.createParagraph();
		XWPFRun r5 = p5.createRun();
		String firstPicture = pictureData.get(dataName1).toString();//转化为字符串数据
		String secondPicture = pictureData.get(dataName2).toString();//转化为字符串数据
		String onePictureCode = firstPicture.substring(22);//将前面没用的数据剔除
		String secondPictureCode = secondPicture.substring(22);//将前面没用的数据剔除
		String imgStr1 = onePictureCode;//获得完整64编码
		String imgStr2 = secondPictureCode;//获得完整64编码
		POIWordUtils.GenerateImage(imgStr1, creatPath);//生成了图片
		POIWordUtils.GenerateImage(imgStr2, creatPath2);//生成了图片
		//FileInputStream f = new FileInputStream("../webapps/IOPM/subject/picture/yuqingtongji.jpg");
		r5.addPicture(new FileInputStream(creatPath), Document.PICTURE_TYPE_JPEG, "", Units.toEMU(size1), Units.toEMU(size2));
		r5.addPicture(new FileInputStream(creatPath2), Document.PICTURE_TYPE_JPEG, "", Units.toEMU(size3), Units.toEMU(size4));
		document.createPicture(p5, document.getAllPictures().size()-1, 0, 0,"");//生成图片
		document.createPicture(p5, document.getAllPictures().size()-1, 0, 0,"");//生成图片
	}

	private void straightMatter(CustomXWPFDocument document,String text) {
		XWPFParagraph p3 = document.createParagraph();
		XWPFRun r3 = p3.createRun();
		r3.setText(text);//正文
		p3.setIndentationFirstLine(400);

	}

	private void creatTableNameAndBlank(XWPFDocument document,String text) {
		XWPFParagraph b2 = document.createParagraph();//创建一个自然段
		XWPFRun br2 = b2.createRun();
		br2.setText("");
		XWPFParagraph b1 = document.createParagraph();//创建一个自然段
		 b1.setAlignment(ParagraphAlignment.CENTER);//居中
		 XWPFRun br1 = b1.createRun();
		 br1.setText(text);
	}

	private void creat_WeiboAndWeixinTable(HttpSession session,String sessionAttribute, XWPFDocument document,String key) throws Exception {
		 HashedMap weiBoRank = (HashedMap) session.getAttribute(sessionAttribute);
		 ArrayList<Object> early = (ArrayList<Object>) weiBoRank.get(key);
		 //Map earlyData = JSONObject.fromObject(early.get(0));
		 XWPFTable weiBoRankTableEarly = document.createTable(early.size()+1,5);
		 //weiBoRankTableEarly.getRow(0).getCell(0).setText("排名");
		 smallFont(weiBoRankTableEarly, 0, 0, "排名");
		 smallFont(weiBoRankTableEarly, 0, 1, "标题");
		 smallFont(weiBoRankTableEarly, 0, 2, "时间");
		 smallFont(weiBoRankTableEarly, 0, 3, "站名");
		 smallFont(weiBoRankTableEarly, 0, 4, "URL");
/*		 smallFont(weiBoRankTableEarly, 0, 5, "转发数");
		 smallFont(weiBoRankTableEarly, 0, 6, "评论数");*/
		 //weiBoRankTableEarly.setCellMargins(1000, 1000, 1000, 1000);设置边框
		 
		 
		 POIWordUtils.setCellWidthAndVAlign(weiBoRankTableEarly.getRow(0).getCell(0), "1200", STTblWidth.DXA, STVerticalJc.TOP);
		 POIWordUtils.setCellWidthAndVAlign(weiBoRankTableEarly.getRow(0).getCell(1), "1400", STTblWidth.DXA, STVerticalJc.TOP);
		 POIWordUtils.setCellWidthAndVAlign(weiBoRankTableEarly.getRow(0).getCell(2), "1400", STTblWidth.DXA, STVerticalJc.TOP);
		 POIWordUtils.setCellWidthAndVAlign(weiBoRankTableEarly.getRow(0).getCell(3), "1400", STTblWidth.DXA, STVerticalJc.TOP);
		 POIWordUtils.setCellWidthAndVAlign(weiBoRankTableEarly.getRow(0).getCell(4), "3200", STTblWidth.DXA, STVerticalJc.TOP);
		 /*POIWordUtils.setCellWidthAndVAlign(weiBoRankTableEarly.getRow(0).getCell(5), "600", STTblWidth.DXA, STVerticalJc.TOP);
		 POIWordUtils.setCellWidthAndVAlign(weiBoRankTableEarly.getRow(0).getCell(6), "600", STTblWidth.DXA, STVerticalJc.TOP);*/
		 String content =" ";
		 for (int i = 0; i < early.size(); i++) {
			 Map oneEarlyDate = JSONObject.fromObject(early.get(i));
			 if(sessionAttribute=="weiXinRank"){
				 content = oneEarlyDate.get("TITLE").toString();
			 }else{
				 content = oneEarlyDate.get("CONTENT").toString();			 
			 }
			content = cutContent(content);
			String release_time = oneEarlyDate.get("RELEASE_TIME").toString().replace("null", "0");
			String website_name = oneEarlyDate.get("WEBSITE_NAME").toString().replace("null", "0");
			String url = oneEarlyDate.get("URL").toString().replace("null", "0");
			/*String up_count = oneEarlyDate.get("UP_COUNT").toString().replace("null", "0");
			String transmit_count = oneEarlyDate.get("TRANSMIT_COUNT").toString().replace("null", "0");
			String comment_count = oneEarlyDate.get("COMMENT_COUNT").toString().replace("null", "0");*/
			
			smallFont(weiBoRankTableEarly, i+1, 0, Integer.valueOf(i+1).toString());
			smallFont(weiBoRankTableEarly, i+1, 1, content);
			smallFont(weiBoRankTableEarly, i+1, 2, release_time);
			smallFont(weiBoRankTableEarly, i+1, 3, website_name);
			smallFont(weiBoRankTableEarly, i+1, 4, url);
/*			smallFont(weiBoRankTableEarly, i+1, 4, up_count);
			smallFont(weiBoRankTableEarly, i+1, 5, transmit_count);
			smallFont(weiBoRankTableEarly, i+1, 6, comment_count);*/			

			
			POIWordUtils.setCellWidthAndVAlign(weiBoRankTableEarly.getRow(i+1).getCell(0), "1200", STTblWidth.DXA, STVerticalJc.TOP);
			POIWordUtils.setCellWidthAndVAlign(weiBoRankTableEarly.getRow(i+1).getCell(1), "1400", STTblWidth.DXA, STVerticalJc.TOP);
			POIWordUtils.setCellWidthAndVAlign(weiBoRankTableEarly.getRow(i+1).getCell(2), "1400", STTblWidth.DXA, STVerticalJc.TOP);
			POIWordUtils.setCellWidthAndVAlign(weiBoRankTableEarly.getRow(i+1).getCell(3), "1400", STTblWidth.DXA, STVerticalJc.TOP);
			POIWordUtils.setCellWidthAndVAlign(weiBoRankTableEarly.getRow(i+1).getCell(4), "3200", STTblWidth.DXA, STVerticalJc.TOP);
/*			POIWordUtils.setCellWidthAndVAlign(weiBoRankTableEarly.getRow(i+1).getCell(5), "600", STTblWidth.DXA, STVerticalJc.TOP);
			POIWordUtils.setCellWidthAndVAlign(weiBoRankTableEarly.getRow(i+1).getCell(6), "600", STTblWidth.DXA, STVerticalJc.TOP);*/
				
		 }
	}

	private void smallFont(XWPFTable weiBoRankTableEarly,int row , int rowCell,String text) {
		XWPFTableCell cell = weiBoRankTableEarly.getRow(row).getCell(rowCell);
		 XWPFParagraph p = cell.addParagraph(); 
		 XWPFRun createRun = p.createRun();
		 createRun.setText(text);
		 createRun.setFontSize(8);
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	

	//查询信息总数量
	public Integer selectSubjectCount() throws Exception {
		Integer totalCounts ;//信息总数量
		totalCounts = subjectDao.selectTotalCount();
		//System.out.println("信息总数量: " + totalCounts);
		Map<String, Object> totalCount = new HashMap();
		totalCount.put("totalCounts", totalCounts);
		this.objectToJson(totalCount);
		return null;
	}

	//根据id删除信息
	public String delectSubjectOne() throws Exception {

		subjectDao.delectSubject(id);
		HttpServletResponse response = ServletActionContext.getResponse();
		response.setContentType("text/html;charset=UTF-8");// 解决中文乱码
		response.getWriter().print("{success:true,info:'删除成功'}");
		return null;
	}


	//分页查询所有专题
	public String selectSubjectPage() throws Exception {

		
		HttpServletResponse response = ServletActionContext.getResponse();
		response.setContentType("text/html;charset=UTF-8");// 解决中文乱码
		Map<String, Object> taskMap = new HashMap();

		taskMap.put("start", page);
		taskMap.put("limit", size);
		//String name="十";
		taskMap.put("names", name);


		//System.out.println("roleList-+-+-+-" + roleList);
/*		String sqlString="";
		if (name.equals(null)||name.equals(""))
		{
			sqlString="order by t.update_time desc";
		}else {
			sqlString="and name like '%" + name + "%' order by abs(length(name) - length('" + name + "')";
		}
		taskMap.put("names", sqlString);*/
		List<TabIopmSubject> roleList = subjectDao.selectSubjectAll(taskMap);

		////System.out.println("roleList-+-+-+-" + roleList);
	Integer totalCounts ;//信息总数量
		totalCounts = subjectDao.selectTotalCount();
		Map<String, Object> resultMap = new HashMap();
		resultMap.put("resultList", roleList);
		resultMap.put("totalCounts", totalCounts);

		
		this.objectToJson(resultMap);
		////System.out.println("查到的分页数据-+-+-+" + resultMap);
		return null;
	}

	//根据id修改专题的方法
	public String updateSubjectOne() throws Exception {
		//List<TabIopmSubject> subject = new ArrayList<TabIopmSubject>();
		HttpServletResponse response = ServletActionContext.getResponse();
		response.setContentType("text/html;charset=UTF-8");// 解决中文乱码
		
		Map<String, Object> map = new HashMap();
		//if (id != null && !"".equals("id"))
		map.put("id", id);
		//if (name != null && !"".equals(name))
		map.put("name", name);
		//if (keyword1 != null && !"".equals(keyword1))
		map.put("keyword1", keyword1);
		//if (keyword2 != null && !"".equals(keyword2))
		map.put("keyword2", keyword2);
		//if (startTime != null && !"".equals(startTime))
		map.put("startTime", startTime);
		//if (endTime != null && !"".equals(endTime))
		map.put("endTime", endTime);
		subjectDao.updateSubjectOne(map);
		response.getWriter().print("{success:true,info:'修改成功'}");
		return null;
	}
	
	//舆情统计 ---查询线索的name和id
	public String getClassifyMsgCount() throws Exception{
		
		loadSubjectMessage();
		List<Map<String ,Object>> zeroclassify = subjectDao.selectZeroIdAndName();
		//String  subjectStart = subjectDao.subjectStart(id);        //截止日期
		//System.out.println("开始检测时间为"+startTime);
		//String  subjectEndTim = subjectDao.subjectEnd(keyWord1);    //截止日期
		//System.out.println("截止时间为"+endTime);
		String titleName=subjectDao.selectTitle(id);
		List list1 = new ArrayList();
		List list3 = new ArrayList();
		int  characterCount= 0;
		int  institutionsCount= 0;
		int  policyCount= 0;
		//循环线索id
		for (int j = 0; j < zeroclassify.size(); j++) 
		{
			Map map2 = new HashedMap();
			String ids= "";
			String idss= "";
			
			Map<String, Object> map = zeroclassify.get(j);
			String name = (String) map.get("ZERO_NAME");        //线索的名字
			Object Clueid = map.get("ZERO_ID");                 //线索的Id
			ESSearchUtils es = ESSearchUtils.getInstance();
			
			//根据线索id查出字表id
			String sql1="SELECT ID from tab_iopm_clues where ZERO_ID = "+ Clueid ;
			List<Object> resultList =es.searchBySql(sql1);
			ArrayList<Object> resList = (ArrayList<Object>)resultList.get(1);
			
			
			//循环求出ids
			for (int i = 0; i < resList.size(); i++) {
				Object resultIds = resList.get(i);
				JSONObject resIdsJson = JSONObject.fromObject(resultIds);
				if (i==0) {
					ids="CLUES_IDS like '%" + resIdsJson.get("ID").toString() + "%' ";
					
				}else {
					ids= ids +"or CLUES_IDS like '%"+resIdsJson.get("ID") + "%' "; 
				}
			}
			
			for (int i = 0; i < resList.size(); i++) {
				Object resultIds = resList.get(i);
				JSONObject resIdsJson = JSONObject.fromObject(resultIds);
				if (i==0) {
					idss="CLUES_IDS = '" + resIdsJson.get("ID").toString() + "'";
					
				}else {
					idss= idss + "or CLUES_IDS = '" + resIdsJson.get("ID").toString() +"'";
				}
			}
			//查询信息总数量
			// String
			// sql11="SELECT count(CLUES_IDS) from tab_iopm_article_info where (RELEASE_TIME >= "+
			// "'"+ startTime.toString() +"'"+" and RELEASE_TIME <= "+ "'"+
			// endTime.toString() +"') and (CONTENT = '" + keyword1 +
			// "' or TITLE = '" + keyword1 + "') and (CLUES_IDS = " + ids + ")"
			// ;
			String sql11 = subjectDao.splitKeyWord1(
					"SELECT count(CLUES_IDS) from tab_iopm_article_info where (RELEASE_TIME >= "
							+ "'" + startTime.toString() + "'"
							+ " and RELEASE_TIME <= " + "'"
							+ endTime.toString() + "') and", keyword1, " and ("
							+ ids + ")");
			List<Object> informationCountList = es.searchBySql(sql11);
			int informationCount = (Integer) informationCountList.get(0);
			
			if (j==0)
			{
				characterCount=informationCount;
			}else if(j==3){
				institutionsCount=informationCount;
			}else if (j==2)
			{
				policyCount=informationCount;
			}
			
            //查询负面信息量
     		//String sql="SELECT count(EMOTION) EMOTIONCOUNT from tab_iopm_article_info where RELEASE_TIME >= "+ "'"+ startTime.toString() +"'"+" and RELEASE_TIME <= "+ "'"+ endTime.toString() +"' and (CONTENT like '%" + keyword1 + "%' or TITLE like '%" + keyword1 + "%') and (CLUES_IDS like " + ids + ") and EMOTION = 2 " ;
			String sql = subjectDao
					.splitKeyWord1(
							"SELECT count(EMOTION) EMOTIONCOUNT from tab_iopm_article_info where RELEASE_TIME >= "
									+ "'"
									+ startTime.toString()
									+ "'"
									+ " and RELEASE_TIME <= "
									+ "'"
									+ endTime.toString() + "' and", keyword1,
							" and (" + idss + ") and EMOTION = 2 ");
			List<Object> negativeEmotionCountList = es.searchBySql(sql);
			int negativeEmotionCount = (Integer) negativeEmotionCountList
					.get(0);

			//将查询结果存到map集合,再将map存到list集合
		    map2.put("name", name);
		    map2.put("totalMsg", informationCount);
		    map2.put("negativeEmotionCount", negativeEmotionCount);
		    list1.add(map2);
		    list3.add(map2);
		    //System.out.println("informationCount的值" + informationCount);
		}
		
		//按照总信息量排序
		List totalSort = sort(list1, new Comparator<Map>(){

			public int compare(Map o3, Map o4)
			{
				// TODO Auto-generated method stub
				return (Integer)o4.get("totalMsg") - (Integer)o3.get("totalMsg");
			}
		});
		//System.out.println("totalSort的值-+-+-+-+" + totalSort);
		//按照负面信息量排序
		List negativeSort = sort(list3, new Comparator<Map>(){
			public int compare(Map o1, Map o2)
			{
				// TODO Auto-generated method stub
				return (Integer)o2.get("negativeEmotionCount") - (Integer)o1.get("negativeEmotionCount");
			}
		});
		
		Map resultMap = new HashedMap();
		Map map1 = new HashedMap();
		//模块下的概述
		
		//概述
		String c="";
		String   b="";
		for (int i = 0; i < negativeSort.size(); i++)
		{
			String  a="";
			map1=(Map) negativeSort.get(i);
			a=map1.get("name").toString()+map1.get("totalMsg").toString()+"篇,";
			//System.out.println("我要的map ："+ a );
			
			//如果总信息量为空，则不取值
			if (map1.get("totalMsg").equals(0))
			{
				a="";
			}
			
			//判断出最相关的部门
			if (i==0)
			{
				c=map1.get("name").toString();
			}
			
			b=b+a;
		}
		TabIopmSubject tabIopmSubject = new TabIopmSubject();
	    tabIopmSubject.getName();
	    
		String gaiShu="截止" + endTime + "," + titleName + "事件涉及到的" + b + "总体来说，该事件的发生与" + c + "密切相关。";
		////System.out.println("截止" + subjectStart + "," + subjectName + "事件涉及到的" + b + "总体来说，该事件的发生与" + c + "密切相关");
		
		List list2 = new ArrayList(); 
		list2.add(gaiShu);
		//System.out.println("概述为：+-+-+-+-+-+" +list2);
		
		resultMap.put("totalSort", totalSort);
		resultMap.put("negativeSort", negativeSort);
		resultMap.put("summarize", list2);
		HttpServletRequest request = ServletActionContext.getRequest();
		request.getSession().setAttribute("ClassifyMsgCount",resultMap);
		this.objectToJson(resultMap);
		//System.out.println("totalSort的值-+-+-+-+" + totalSort);
		//System.out.println("为难的数据-+-+-+-+-+-+-+" + resultMap.get("totalSort"));
		return null;
	}


	
	//事件溯源分析
	public String getTimeInfoType() throws Exception{
		//String keyWord1 = subjectDao.subjectKeyWord1(id);
		loadSubjectMessage();
		ESSearchUtils es = ESSearchUtils.getInstance();

     	//String  subjectStart = subjectDao.subjectStart(186L);        //截止日期
		//System.out.println("开始检测时间为"+startTime);
		//String  subjectEndTim = subjectDao.subjectEnd(keyWord1);    //截止日期
		//System.out.println("截止时间为"+endTime);
	    List list=new ArrayList();
		
		for (int i = 1; i <=3; i++)
		{
			Map map = new HashedMap();
			
			//String sql = "select INFO_TYPE, TITLE, AUTHOR, WEBSITE_NAME, RELEASE_TIME, URL from tab_iopm_article_info where  (RELEASE_TIME <= '"+endTime+"' and RELEASE_TIME >= '"+startTime+"') and TITLE like '%" +keyword1+ "%' and INFO_TYPE = " + i + " limit 1";
			String sql = "";
			if (i == 3){
				sql = subjectDao.splitKeyWord1("select INFO_TYPE, CONTENT, AUTHOR, WEBSITE_NAME, RELEASE_TIME, URL from tab_iopm_article_info where  (RELEASE_TIME <= '"+endTime+"' and RELEASE_TIME >= '"+startTime+"') and", keyword1, "and INFO_TYPE = " + i + " limit 1");
			}else{
				sql = subjectDao.splitKeyWord1("select INFO_TYPE, TITLE, AUTHOR, WEBSITE_NAME, RELEASE_TIME, URL from tab_iopm_article_info where  (RELEASE_TIME <= '"+endTime+"' and RELEASE_TIME >= '"+startTime+"') and", keyword1, "and INFO_TYPE = " + i + " limit 1");
			}
			List<Object> resultsList= es.searchBySql(sql);
			//System.out.println(resultsList);
			ArrayList<Object> result = (ArrayList<Object>)resultsList.get(1);
			if(result.size()>0){
				Object res = result.get(0);
				System.out.println("result-+-+-+-+-+-+"+result);
				
				System.out.println("小map的值-+-+-+-+-+-+"+res);
				JSONObject endResult = JSONObject.fromObject(res);
				Object releaseTime2 =  endResult.get("RELEASE_TIME");
				Object websiteName2 = endResult.get("WEBSITE_NAME");
				Object infoType2 = endResult.get("INFO_TYPE");
				Object title2 =null;
				if (i==3)
				{
					 title2 = endResult.get("CONTENT").toString().replace("\r\n", "");
				}else {
					title2 = endResult.get("TITLE");
				}
				Object url2 = endResult.get("URL");
				Object author2 = endResult.get("AUTHOR");

				map.put("releaseTime", releaseTime2);
				map.put("websiteName", websiteName2);
				map.put("infoType", infoType2);
				map.put("title", title2);
				map.put("url", url2);
				map.put("author", author2);
				
				SimpleDateFormat format = new SimpleDateFormat("yyyy-M-dd HH:mm:ss"); 
		        Date releaseTime1=format.parse(map.get("releaseTime").toString());
		        
				long time=releaseTime1.getTime();
				map.put("time", time);
		        
				//res.get("RELEASE_TIME");
				if (result.size()==0)
				{
				
				}else {
					list.add(map);
				}
			}else{			
			map.put("releaseTime", " ");
			map.put("websiteName", " ");
			map.put("infoType", " ");
			map.put("title", " ");
			map.put("url", " ");
			map.put("author", " ");
			}
		
		}

		//this.arrayToJson(list);
		////System.out.println("list的值-+-+-+-+"+list);
		//按照发布时间排序
		List releaseTimePaiXu = sort(list, new Comparator<Map>(){

			public int compare(Map o1, Map o2)
			{
				// TODO Auto-generated method stub
				return (int)( (Long)o1.get("time") - (Long)o2.get("time"));
			}
			
		});
		
		//概述
		if(releaseTimePaiXu.size()>2){
		Map  map1=(Map) releaseTimePaiXu.get(0);
		Map  map2=(Map) releaseTimePaiXu.get(1);
		Map  map3=(Map) releaseTimePaiXu.get(2);
		////System.out.println("map1的值-+-+-+-+-+" + map1);
		
		Map mapGaiShu = new HashedMap();
		mapGaiShu.put("releaseTime", map1.get("releaseTime"));
		Object author=map1.get("author");
		//判断作者是否为空
		if (author.toString() == "null"){
			author ="";
		}else {
			author =author+"在";
		}
		mapGaiShu.put("author", author);
	   // mapGaiShu.put("author", map1.get("author"));
		mapGaiShu.put("websiteName", map1.get("websiteName"));
		mapGaiShu.put("title", map1.get("title"));
		mapGaiShu.put("infoType", map1.get("infoType"));
		mapGaiShu.put("firstWebsiteName", map2.get("websiteName"));
		mapGaiShu.put("secondWebsiteName", map3.get("websiteName"));
		
		////System.out.println("经传播溯源分析发现," +mapGaiShu.get("releaseTime") +"," +  mapGaiShu.get("author").toString()  + mapGaiShu.get("websiteName")+"站点发布标题为《" + mapGaiShu.get("title") + "》的" + mapGaiShu.get("infoType") + "(类别1为新闻，2为微信，3为微博)论坛信息，是该事件传播的源头信息。其后" + mapGaiShu.get("firstWebsiteName") +"、"+ mapGaiShu.get("secondWebsiteName") + "等站点相继发布相关信息，事件传播影响力渐次扩大.");

		Map resultMap = new HashedMap();
		resultMap.put("releaseTimePaiXu", releaseTimePaiXu);
		resultMap.put("gaiShu", mapGaiShu);
		HttpServletRequest request = ServletActionContext.getRequest();
		request.getSession().setAttribute("suyuan",resultMap);
		this.objectToJson(resultMap);
		}
		if(releaseTimePaiXu.size()==2){
			Map  map1=(Map) releaseTimePaiXu.get(0);
			Map  map2=(Map) releaseTimePaiXu.get(1);
			////System.out.println("map1的值-+-+-+-+-+" + map1);
			
			Map mapGaiShu = new HashedMap();
			mapGaiShu.put("releaseTime", map1.get("releaseTime"));
			Object author=map1.get("author");
			//判断作者是否为空
			if (author.toString() == "null"){
				author ="";
			}else {
				author =author+"在";
			}
			mapGaiShu.put("author", author);
		   // mapGaiShu.put("author", map1.get("author"));
			mapGaiShu.put("websiteName", map1.get("websiteName"));
			mapGaiShu.put("title", map1.get("title"));
			mapGaiShu.put("infoType", map1.get("infoType"));
			mapGaiShu.put("firstWebsiteName", map2.get("websiteName"));
			mapGaiShu.put("secondWebsiteName", "");
			
			////System.out.println("经传播溯源分析发现," +mapGaiShu.get("releaseTime") +"," +  mapGaiShu.get("author").toString()  + mapGaiShu.get("websiteName")+"站点发布标题为《" + mapGaiShu.get("title") + "》的" + mapGaiShu.get("infoType") + "(类别1为新闻，2为微信，3为微博)论坛信息，是该事件传播的源头信息。其后" + mapGaiShu.get("firstWebsiteName") +"、"+ mapGaiShu.get("secondWebsiteName") + "等站点相继发布相关信息，事件传播影响力渐次扩大.");

			Map resultMap = new HashedMap();
			resultMap.put("releaseTimePaiXu", releaseTimePaiXu);
			resultMap.put("gaiShu", mapGaiShu);
			HttpServletRequest request = ServletActionContext.getRequest();
			request.getSession().setAttribute("suyuan",resultMap);
			this.objectToJson(resultMap);
		}
		if(releaseTimePaiXu.size()==1){
			Map  map1=(Map) releaseTimePaiXu.get(0);
			
			////System.out.println("map1的值-+-+-+-+-+" + map1);
			
			Map mapGaiShu = new HashedMap();
			mapGaiShu.put("releaseTime", map1.get("releaseTime"));
			Object author=map1.get("author");
			//判断作者是否为空
			if (author.toString() == "null"){
				author ="";
			}else {
				author =author+"在";
			}
			mapGaiShu.put("author", author);
		   // mapGaiShu.put("author", map1.get("author"));
			mapGaiShu.put("websiteName", map1.get("websiteName"));
			mapGaiShu.put("title", map1.get("title"));
			mapGaiShu.put("infoType", map1.get("infoType"));
			mapGaiShu.put("firstWebsiteName", "");
			mapGaiShu.put("secondWebsiteName", "");
			
			////System.out.println("经传播溯源分析发现," +mapGaiShu.get("releaseTime") +"," +  mapGaiShu.get("author").toString()  + mapGaiShu.get("websiteName")+"站点发布标题为《" + mapGaiShu.get("title") + "》的" + mapGaiShu.get("infoType") + "(类别1为新闻，2为微信，3为微博)论坛信息，是该事件传播的源头信息。其后" + mapGaiShu.get("firstWebsiteName") +"、"+ mapGaiShu.get("secondWebsiteName") + "等站点相继发布相关信息，事件传播影响力渐次扩大.");

			Map resultMap = new HashedMap();
			resultMap.put("releaseTimePaiXu", releaseTimePaiXu);
			resultMap.put("gaiShu", mapGaiShu);
			HttpServletRequest request = ServletActionContext.getRequest();
			request.getSession().setAttribute("suyuan",resultMap);
			this.objectToJson(resultMap);
		}
		if(releaseTimePaiXu.size()==0){
			
			////System.out.println("map1的值-+-+-+-+-+" + map1);
			
			Map mapGaiShu = new HashedMap();
			mapGaiShu.put("releaseTime", "");
			//Object author=map1.get("author");
			//判断作者是否为空

			mapGaiShu.put("author", "");
		   // mapGaiShu.put("author", map1.get("author"));
			mapGaiShu.put("websiteName", "");
			mapGaiShu.put("title", "");
			mapGaiShu.put("infoType", "");
			mapGaiShu.put("firstWebsiteName", "");
			mapGaiShu.put("secondWebsiteName", "");
			
			////System.out.println("经传播溯源分析发现," +mapGaiShu.get("releaseTime") +"," +  mapGaiShu.get("author").toString()  + mapGaiShu.get("websiteName")+"站点发布标题为《" + mapGaiShu.get("title") + "》的" + mapGaiShu.get("infoType") + "(类别1为新闻，2为微信，3为微博)论坛信息，是该事件传播的源头信息。其后" + mapGaiShu.get("firstWebsiteName") +"、"+ mapGaiShu.get("secondWebsiteName") + "等站点相继发布相关信息，事件传播影响力渐次扩大.");

			Map resultMap = new HashedMap();
			resultMap.put("releaseTimePaiXu", releaseTimePaiXu);
			resultMap.put("gaiShu", mapGaiShu);
			HttpServletRequest request = ServletActionContext.getRequest();
			request.getSession().setAttribute("suyuan",resultMap);
			this.objectToJson(resultMap);
		}
			return null;
	  }

	//事件溯源分析
/*	public String getTimeInfoType() throws Exception{
		//String keyWord1 = subjectDao.subjectKeyWord1(id);
		loadSubjectMessage();
		ESSearchUtils es = ESSearchUtils.getInstance();

     	//String  subjectStart = subjectDao.subjectStart(id);        //截止日期
		//System.out.println("开始检测时间为"+startTime);
		//String  subjectEndTim = subjectDao.subjectEnd(keyWord1);    //截止日期
		//System.out.println("截止时间为"+endTime);
	    List list=new ArrayList();
		
		for (int i = 1; i <=3; i++)
		{
			Map map = new HashedMap();
			
			//String sql = "select INFO_TYPE, TITLE, AUTHOR, WEBSITE_NAME, RELEASE_TIME, URL from tab_iopm_article_info where  (RELEASE_TIME <= '"+endTime+"' and RELEASE_TIME >= '"+startTime+"') and TITLE like '%" +keyword1+ "%' and INFO_TYPE = " + i + " limit 1";
			String sql = "";
			if (i == 3){
				sql = subjectDao.splitKeyWord1("select INFO_TYPE, CONTENT, AUTHOR, WEBSITE_NAME, RELEASE_TIME, URL from tab_iopm_article_info where  (RELEASE_TIME <= '"+endTime+"' and RELEASE_TIME >= '"+startTime+"') and", keyword1, "and INFO_TYPE = " + i + " limit 1");
			}else{
				sql = subjectDao.splitKeyWord1("select INFO_TYPE, TITLE, AUTHOR, WEBSITE_NAME, RELEASE_TIME, URL from tab_iopm_article_info where  (RELEASE_TIME <= '"+endTime+"' and RELEASE_TIME >= '"+startTime+"') and", keyword1, "and INFO_TYPE = " + i + " limit 1");
			}
			List<Object> resultsList= es.searchBySql(sql);
			//System.out.println(resultsList);
			ArrayList<Object> result = (ArrayList<Object>)resultsList.get(1);
			if(result.size()>0){
				Object res = result.get(0);
				////System.out.println("result-+-+-+-+-+-+"+result);
				
				////System.out.println("小map的值-+-+-+-+-+-+"+res);
				JSONObject endResult = JSONObject.fromObject(res);
				Object releaseTime2 =  endResult.get("RELEASE_TIME");
				Object websiteName2 = endResult.get("WEBSITE_NAME");
				Object infoType2 = endResult.get("INFO_TYPE");
				Object title2 =null;
				if (i==3)
				{
					 title2 = endResult.get("CONTENT").toString().replace("\r\n", "");
				}else {
					title2 = endResult.get("TITLE");
				}
				Object url2 = endResult.get("URL");
				Object author2 = endResult.get("AUTHOR");

				map.put("releaseTime", releaseTime2);
				map.put("websiteName", websiteName2);
				map.put("infoType", infoType2);
				map.put("title", title2);
				map.put("url", url2);
				map.put("author", author2);
				
				SimpleDateFormat format = new SimpleDateFormat("yyyy-M-dd HH:mm:ss"); 
		        Date releaseTime1=format.parse(map.get("releaseTime").toString());
		        
				long time=releaseTime1.getTime();
				map.put("time", time);
		        
				//res.get("RELEASE_TIME");
				if (result.size()==0)
				{
				
				}else {
					list.add(map);
				}
			}
			map.put("releaseTime", " ");
			map.put("websiteName", " ");
			map.put("infoType", " ");
			map.put("title", " ");
			map.put("url", " ");
			map.put("author", " ");
			
		}

		//this.arrayToJson(list);
		////System.out.println("list的值-+-+-+-+"+list);
		//按照发布时间排序
		List releaseTimePaiXu = sort(list, new Comparator<Map>(){

			public int compare(Map o1, Map o2)
			{
				// TODO Auto-generated method stub
				return (int)( (Long)o1.get("time") - (Long)o2.get("time"));
			}
			
		});
		
		//概述
		if(releaseTimePaiXu.size()>2){
		Map  map1=(Map) releaseTimePaiXu.get(0);
		Map  map2=(Map) releaseTimePaiXu.get(1);
		Map  map3=(Map) releaseTimePaiXu.get(2);
		////System.out.println("map1的值-+-+-+-+-+" + map1);
		
		Map mapGaiShu = new HashedMap();
		mapGaiShu.put("releaseTime", map1.get("releaseTime"));
		Object author=map1.get("author");
		//判断作者是否为空
		if (author.toString() == "null"){
			author ="";
		}else {
			author =author+"在";
		}
		mapGaiShu.put("author", author);
	   // mapGaiShu.put("author", map1.get("author"));
		mapGaiShu.put("websiteName", map1.get("websiteName"));
		mapGaiShu.put("title", map1.get("title"));
		mapGaiShu.put("infoType", map1.get("infoType"));
		mapGaiShu.put("firstWebsiteName", map2.get("websiteName"));
		mapGaiShu.put("secondWebsiteName", map3.get("websiteName"));
		
		////System.out.println("经传播溯源分析发现," +mapGaiShu.get("releaseTime") +"," +  mapGaiShu.get("author").toString()  + mapGaiShu.get("websiteName")+"站点发布标题为《" + mapGaiShu.get("title") + "》的" + mapGaiShu.get("infoType") + "(类别1为新闻，2为微信，3为微博)论坛信息，是该事件传播的源头信息。其后" + mapGaiShu.get("firstWebsiteName") +"、"+ mapGaiShu.get("secondWebsiteName") + "等站点相继发布相关信息，事件传播影响力渐次扩大.");

		Map resultMap = new HashedMap();
		resultMap.put("releaseTimePaiXu", releaseTimePaiXu);
		resultMap.put("gaiShu", mapGaiShu);
		this.objectToJson(resultMap);
		}
		if(releaseTimePaiXu.size()==2){
			Map  map1=(Map) releaseTimePaiXu.get(0);
			Map  map2=(Map) releaseTimePaiXu.get(1);
			////System.out.println("map1的值-+-+-+-+-+" + map1);
			
			Map mapGaiShu = new HashedMap();
			mapGaiShu.put("releaseTime", map1.get("releaseTime"));
			Object author=map1.get("author");
			//判断作者是否为空
			if (author.toString() == "null"){
				author ="";
			}else {
				author =author+"在";
			}
			mapGaiShu.put("author", author);
		   // mapGaiShu.put("author", map1.get("author"));
			mapGaiShu.put("websiteName", map1.get("websiteName"));
			mapGaiShu.put("title", map1.get("title"));
			mapGaiShu.put("infoType", map1.get("infoType"));
			
			mapGaiShu.put("firstWebsiteName", map2.get("websiteName"));
			mapGaiShu.put("secondWebsiteName", "");
			
			////System.out.println("经传播溯源分析发现," +mapGaiShu.get("releaseTime") +"," +  mapGaiShu.get("author").toString()  + mapGaiShu.get("websiteName")+"站点发布标题为《" + mapGaiShu.get("title") + "》的" + mapGaiShu.get("infoType") + "(类别1为新闻，2为微信，3为微博)论坛信息，是该事件传播的源头信息。其后" + mapGaiShu.get("firstWebsiteName") +"、"+ mapGaiShu.get("secondWebsiteName") + "等站点相继发布相关信息，事件传播影响力渐次扩大.");

			Map resultMap = new HashedMap();
			resultMap.put("releaseTimePaiXu", releaseTimePaiXu);
			resultMap.put("gaiShu", mapGaiShu);
			this.objectToJson(resultMap);
		}
		if(releaseTimePaiXu.size()==1){
			Map  map1=(Map) releaseTimePaiXu.get(0);
			
			////System.out.println("map1的值-+-+-+-+-+" + map1);
			
			Map mapGaiShu = new HashedMap();
			mapGaiShu.put("releaseTime", map1.get("releaseTime"));
			Object author=map1.get("author");
			//判断作者是否为空
			if (author.toString() == "null"){
				author ="";
			}else {
				author =author+"在";
			}
			mapGaiShu.put("author", author);
		   // mapGaiShu.put("author", map1.get("author"));
			mapGaiShu.put("websiteName", map1.get("websiteName"));
			mapGaiShu.put("title", map1.get("title"));
			mapGaiShu.put("infoType", map1.get("infoType"));
			mapGaiShu.put("firstWebsiteName", "");
			mapGaiShu.put("secondWebsiteName", "");
			
			////System.out.println("经传播溯源分析发现," +mapGaiShu.get("releaseTime") +"," +  mapGaiShu.get("author").toString()  + mapGaiShu.get("websiteName")+"站点发布标题为《" + mapGaiShu.get("title") + "》的" + mapGaiShu.get("infoType") + "(类别1为新闻，2为微信，3为微博)论坛信息，是该事件传播的源头信息。其后" + mapGaiShu.get("firstWebsiteName") +"、"+ mapGaiShu.get("secondWebsiteName") + "等站点相继发布相关信息，事件传播影响力渐次扩大.");

			Map resultMap = new HashedMap();
			resultMap.put("releaseTimePaiXu", releaseTimePaiXu);
			resultMap.put("gaiShu", mapGaiShu);
			this.objectToJson(resultMap);
		}
		if(releaseTimePaiXu.size()==0){
			
			////System.out.println("map1的值-+-+-+-+-+" + map1);
			
			Map mapGaiShu = new HashedMap();
			mapGaiShu.put("releaseTime", "");
			//Object author=map1.get("author");
			//判断作者是否为空
			if (author.toString() == "null"){
				author ="";
			}else {
				author =author+"在";
			}
			mapGaiShu.put("author", "");
		   // mapGaiShu.put("author", map1.get("author"));
			mapGaiShu.put("websiteName", "");
			mapGaiShu.put("title", "");
			mapGaiShu.put("infoType", "");
			mapGaiShu.put("firstWebsiteName", "");
			mapGaiShu.put("secondWebsiteName", "");
			
			////System.out.println("经传播溯源分析发现," +mapGaiShu.get("releaseTime") +"," +  mapGaiShu.get("author").toString()  + mapGaiShu.get("websiteName")+"站点发布标题为《" + mapGaiShu.get("title") + "》的" + mapGaiShu.get("infoType") + "(类别1为新闻，2为微信，3为微博)论坛信息，是该事件传播的源头信息。其后" + mapGaiShu.get("firstWebsiteName") +"、"+ mapGaiShu.get("secondWebsiteName") + "等站点相继发布相关信息，事件传播影响力渐次扩大.");

			Map resultMap = new HashedMap();
			resultMap.put("releaseTimePaiXu", releaseTimePaiXu);
			resultMap.put("gaiShu", mapGaiShu);
			this.objectToJson(resultMap);
		}
			return null;
	  }
*/
	

	
	//根据情感波动查询
		public String getEmotionBoDong() throws Exception{
			loadSubjectMessage();
			ESSearchUtils es = ESSearchUtils.getInstance();
			String sql1="";//日期
			String httpValue = PropertiesFactory.getValue("es.http.hosts");

			String keyWord =subjectDao.splitKeyWord1("",keyword1,"");//单独分割出keyword1
			
			Calendar c = Calendar.getInstance();//日历
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			Date startDate = format.parse(startTime);//专题开始时间
			Date endDate = format.parse(endTime);//专题结束时间
			//System.out.println("日期c"+c.getTime());
			c.setTime(startDate);
			c.add(c.DATE, -1);
			//用来存放数量和日期
			Integer a = 0;
			String peakdate =null;
			Map<Object, Object> map = new HashedMap();//用来存放总体数据
			ArrayList<Object> arrayList = new ArrayList<Object>();//存放循环出来的数据
			while (true) {
				String s = subjectDao.date2String(startDate);
				c.add(c.DATE, 1);
				Date time = c.getTime();
				if(time.after(endDate)){
					
					break;
				}else{
					startDate=time;
				}
				String date2String = subjectDao.date2String(time);
				//System.out.println("date2String:"+date2String);
				 sql1="(RELEASE_TIME >= '"+date2String+" 00:00:00' and RELEASE_TIME <= '"+date2String+" 23:59:59') and";
				String json = subjectDao.URLSql(httpValue, "SELECT ID,COUNT(EMOTION),EMOTION FROM tab_iopm_article_info WHERE "+sql1, keyWord, "GROUP BY EMOTION");
				//System.out.println(json);
				JSONObject json_All = JSONObject.fromObject(json);
				JSONObject jsonObject = (JSONObject) json_All.getJSONObject("aggregations").getJSONObject("EMOTION");//获得所要数据
				JSONArray object = (JSONArray) jsonObject.get("buckets");//通过获得buckets来得到一个JSONArray
				Map<Object, Object> valueMap = new HashedMap();
				valueMap.put("TIME", date2String);
				valueMap.put("zheng","0" );
				valueMap.put("fu","0" );
				valueMap.put("zhong","0" );
				int size2 = object.size();
				if(size2==0){
					valueMap.put("zheng", "0");
					valueMap.put("fu", "0");
					valueMap.put("zhong","0");
					arrayList.add(valueMap);
					continue;
				}
				if(size2==1){
					JSONObject key1 = (JSONObject) object.get(0);//获得key=1的
					int object2 = Integer.valueOf(key1.get("key").toString());
					if(object2==1){
						valueMap.put("zheng", key1.get("doc_count"));
					}else if(object2==2){
						valueMap.put("fu", key1.get("doc_count"));
					}else if(object2==3){
						valueMap.put("zhong", key1.get("doc_count"));
					}
					arrayList.add(valueMap);
					continue;
				}
				if(size2==2){
					JSONObject key1 = (JSONObject) object.get(0);//获得key=1的
					JSONObject key2 = (JSONObject) object.get(1);//获得key=2的
					int object2 = Integer.valueOf(key1.get("key").toString());
					int object3 = Integer.valueOf(key2.get("key").toString());
					if(object2==1){
						valueMap.put("zheng", key1.get("doc_count"));
					}
					if(object2==2){
						valueMap.put("fu", key1.get("doc_count"));
					}
					if(object2==3){
						valueMap.put("zhong", key1.get("doc_count"));
					}
					if(object3==1){
						valueMap.put("zheng", key1.get("doc_count"));
					}
					if(object3==2){
						valueMap.put("fu", key1.get("doc_count"));
					}
					if(object3==3){
						valueMap.put("zhong", key1.get("doc_count"));
					}
					arrayList.add(valueMap);
					continue;
				}
				
				JSONObject key1 = (JSONObject) object.get(0);//获得key=1的 正面
				JSONObject key2 = (JSONObject) object.get(1);//获得key=2的 负面
				JSONObject key3 = (JSONObject) object.get(2);//获得key=3的 中立
				valueMap.put("zheng", key1.get("doc_count"));
				valueMap.put("fu", key2.get("doc_count"));
				valueMap.put("zhong", key3.get("doc_count"));
				
				arrayList.add(valueMap);
			}
			Map<Object, Object> allMap = new HashedMap();
			allMap.put("emotionTimeAndcount", arrayList);
			this.objectToJson(allMap);

			return null;
		}
	

	//根据情感波动比例
	public String getEmotionBoDongBiLi() throws Exception{
		loadSubjectMessage();
		
		String keyWord1 = subjectDao.splitKeyWord1("", keyword1, "");
		String sql= " (RELEASE_TIME>='"+startTime+"' and RELEASE_TIME<='"+endTime+"') and";
		String httpValue = PropertiesFactory.getValue("es.http.hosts");
		String json = subjectDao.URLSql(httpValue, "SELECT COUNT(EMOTION),EMOTION FROM tab_iopm_article_info WHERE"+sql, keyWord1, "GROUP BY EMOTION");
		JSONObject jsonAll = JSONObject.fromObject(json);
        JSONObject jsonObject = (JSONObject) jsonAll.getJSONObject("aggregations").getJSONObject("EMOTION");//获得所要数据
		JSONArray object = (JSONArray) jsonObject.get("buckets");//通过获得buckets来得到一个JSONArray
		/*JSONObject key1 = (JSONObject) object.get(0);//获得key=1的 正面
		JSONObject key2 = (JSONObject) object.get(1);//获得key=2的 负面
		JSONObject key3 = (JSONObject) object.get(2);//获得key=3的 中立
*/		Map<Object, Object> map = new HashedMap();//用来存放总体数据
		map.put("zheng", "0");
		map.put("fu", "0");
		map.put("zhong","0");
		int size2 = object.size();
		if(size2==1){
			JSONObject key1 = (JSONObject) object.get(0);//获得key=1的
			int object2 = Integer.valueOf(key1.get("key").toString());
			if(object2==1){
				map.put("zheng", key1.get("doc_count"));
			}else if(object2==2){
				map.put("fu", key1.get("doc_count"));
			}else if(object2==3){
				map.put("zhong", key1.get("doc_count"));
			}

		}
		if(size2==2){
			JSONObject key1 = (JSONObject) object.get(0);//获得key=1的
			JSONObject key2 = (JSONObject) object.get(1);//获得key=2的
			int object2 = Integer.valueOf(key1.get("key").toString());
			int object3 = Integer.valueOf(key2.get("key").toString());
			if(object2==1){
				map.put("zheng", key1.get("doc_count"));
			}
			if(object2==2){
				map.put("fu", key1.get("doc_count"));
			}
			if(object2==3){
				map.put("zhong", key1.get("doc_count"));
			}
			if(object3==1){
				map.put("zheng", key2.get("doc_count"));
			}
			if(object3==2){
				map.put("fu", key2.get("doc_count"));
			}
			if(object3==3){
				map.put("zhong", key2.get("doc_count"));
			}

		}
		

		if(size2>2){
			JSONObject key1 = (JSONObject) object.get(0);//获得key=1的 正面
			JSONObject key2 = (JSONObject) object.get(1);//获得key=2的 负面
			JSONObject key3 = (JSONObject) object.get(2);//获得key=3的 中立
			map.put("zheng", key1.get("doc_count"));
			map.put("fu", key2.get("doc_count"));
			map.put("zhong", key3.get("doc_count"));
		}
		Map<Object, Object> allMap = new HashedMap();
		allMap.put("emotionTimeAndcount", map);
		this.objectToJson(allMap);	
		//this.objectToJson(map);
		
		return null;
	}
	
	
	
	
	
	//添加专题
	public String addSubject() throws Exception{
		//System.out.println("我来添加了");
		TabIopmSubject tabIopmSubject = new TabIopmSubject();
	    tabIopmSubject.setName(name);
	    tabIopmSubject.setStartTime(startTime + " 00:00:00");
	    tabIopmSubject.setEndTime(endTime + " 23:59:59");
	    tabIopmSubject.setKeyword1(keyword1);
	    tabIopmSubject.setKeyword2(keyword2);
/*	    Date date = new Date();
	    DateTime dateTime = new DateTime();
	    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");*/
	   
	subjectDao.addSubject(tabIopmSubject);
		return null;
		
	}
	
	//查询所有专题
	public String selectAllSubject() throws Exception{
		
		List<Map<String, Object>> list2 = subjectDao.selectAllSubject();
		this.arrayToJson(list2);
		return null;
	}
	
	//查询专题信息,用于判断是否有该专题
	public String searchSubject() throws Exception {
		//System.out.println(name);
		List<Map<String, Object>> searchSubject = new ArrayList();
		if (name != null) {
			searchSubject = subjectDao.searchSubject(name);
		}
		this.arrayToJson(searchSubject);
		
		return null;
	}
	
	//模糊查询返回提示框
	public String searchSubjectLikeByName() throws Exception{
		List<Map<String,Object>> selectLikeSubject = subjectDao.selectLikeSubject(subjectName);
		HttpServletResponse response = ServletActionContext.getResponse();
		response.setContentType("text/html;charset=UTF-8");// 解决中文乱码
		if(selectLikeSubject.size()>0){
			Map<String, Object> map = selectLikeSubject.get(0);
			response.getWriter().write(JsonUtils.fromArray(selectLikeSubject));
		}
		response.getWriter().write("");
		return null;
	}

	public String testWordToHtml() throws Exception{
		String filename = "文件尝试.doc";
		 @SuppressWarnings("resource")
		 XWPFDocument document=new XWPFDocument();
		 
		 
		 XWPFParagraph p1=document.createParagraph();
		 p1.setAlignment(ParagraphAlignment.CENTER);
		 XWPFRun r1=p1.createRun();
		 r1.setText("测试标题");
		 
		 XWPFParagraph p2 = document.createParagraph();
		 XWPFRun r2 = p2.createRun();
		 r2.setText("一、舆情概述");//标题
		 r2.setBold(true);//设置为粗体
		 
		 //获取浏览器类型
         String agent = ServletActionContext.getRequest().getHeader("User-Agent");
         String mimeType = ServletActionContext
                  .getServletContext().getMimeType(filename);
         //根据浏览器类型对文件名编码
         filename = FileUtils.encodeDownloadFilename(filename, agent);
         // 4.1一个流：response的输出流
         ServletOutputStream os = ServletActionContext
                  .getResponse().getOutputStream();
         // 4.2两个头之一：content-type，告诉前台浏览器返回数据的格式：xml,css,html,json,xls等等
         ServletActionContext.getResponse().setContentType(mimeType);
         // 4.3两个头之二：content-disposition，告诉前台浏览器数据的打开方式，附件方式打开值如下：attachment;filename=文件名
         ServletActionContext.getResponse().setHeader("content-disposition", "attachment;filename="+filename);
         // 5.将excel通过response返回到前台
         document.write(os);

		return null;
	}
	
	
	
	//舆情概述拼接版(直接可以返回字符串)
	public String startAndEndSbujectById() throws Exception {
		/*HttpServletRequest request = ServletActionContext.getRequest();
		HttpServletResponse response = ServletActionContext.getResponse();
		response.setContentType("application/json;charset=UTF-8");*/
		/*//获取前台传入的id
		String id = request.getParameter("aaa");
		//将获取到的id转换成Long类型
		Long value = Long.valueOf(id);*/
		ESSearchUtils es = ESSearchUtils.getInstance();
		loadSubjectMessage();
		//System.out.println("关键字为"+keyword1);
		//System.out.println("开始检测时间为"+startTime);
		
		//通过id查询出在subject表中最后时间
		//System.out.println("专题最后一次出现的时间为"+endTime);
		
		String sql= " (RELEASE_TIME>='"+startTime+"' and RELEASE_TIME<='"+endTime+"') and";
		
		String sFirst = subjectDao.splitKeyWord1("select RELEASE_TIME from tab_iopm_article_info where "+sql+"(", keyword1, ")order by RELEASE_TIME LIMIT 1");
		List<Object> searchBySql = es.searchBySql(sFirst);
		ArrayList<Object> subjectFirst1 = (ArrayList<Object>) searchBySql.get(1);
		Object subjectFirst=null;
		if(subjectFirst1.size()>0){
			Object subjectFirst10 = subjectFirst1.get(0);
			JSONObject json0 = JSONObject.fromObject(subjectFirst10);
			subjectFirst = json0.get("RELEASE_TIME");			
		}
		//System.out.println("专题第一次出现的时间为"+subjectFirst);
		
		
		//新闻数量	
		String newsSql1 = subjectDao.splitKeyWord1("select count(INFO_TYPE) from tab_iopm_article_info where"+sql, keyword1, "and INFO_TYPE =1");
		List<Object> searchBySqlnews = es.searchBySql(newsSql1);
		//System.out.println("新闻数量为"+searchBySqlnews.get(0));
		
		
		//微博数量
		String weiboSql1 = subjectDao.splitKeyWord1("select count(INFO_TYPE) from tab_iopm_article_info where "+sql, keyword1, "and INFO_TYPE=3");
		List<Object> searchBySqlweibo = es.searchBySql(weiboSql1);
		//System.out.println("微博数量为"+searchBySqlweibo.get(0));
		
		//论坛数量
		String luntanSql1 = subjectDao.splitKeyWord1("select count(INFO_TYPE) from tab_iopm_article_info where "+sql, keyword1, "and INFO_TYPE=4");
		List<Object> searchBySqlluntan = es.searchBySql(luntanSql1);
		//System.out.println("论坛数量为"+searchBySqlluntan.get(0));
		
		//视频
		String videoSql1 = subjectDao.splitKeyWord1("select count(INFO_TYPE) from tab_iopm_article_info where "+sql, keyword1, " and INFO_TYPE=8");
		List<Object> searchBySqlvideo = es.searchBySql(videoSql1);
		//System.out.println("视频数量为"+searchBySqlvideo.get(0));
		
		//微信
		String weixinSql1 = subjectDao.splitKeyWord1("select count(INFO_TYPE) from tab_iopm_article_info where "+sql, keyword1, " and INFO_TYPE=2");
		List<Object> searchBySqlweixin = es.searchBySql(weixinSql1);
		//System.out.println("微信数量为"+searchBySqlweixin.get(0));
		
		//站点与标题
		String webNAndT = subjectDao.splitKeyWord1("", keyword1, "");
		String s1 = webNAndT;
		String webNameAndTitleName ="";
		if(subjectFirst1.size()>0){
			webNameAndTitleName ="SELECT WEBSITE_NAME,TITLE FROM tab_iopm_article_info where  ("+s1+") AND RELEASE_TIME ='"+subjectFirst+"'";	
		}else{
			webNameAndTitleName ="SELECT WEBSITE_NAME,TITLE FROM tab_iopm_article_info where  ("+s1+") AND RELEASE_TIME ="+subjectFirst+"";
		}
		List<Object> webAndTitle = es.searchBySql(webNameAndTitleName);
		ArrayList<Object> webAndTitle1 = (ArrayList<Object>)webAndTitle.get(1);
		Object title ="";
		Object websiteName="";
		if(webAndTitle1.size()>0){
			Object webAndTitle10 = webAndTitle1.get(0);
			JSONObject json2 = JSONObject.fromObject(webAndTitle10);
			title = json2.get("TITLE");
			websiteName = json2.get("WEBSITE_NAME");
		}
		
		
		//System.out.println("站点名称为"+websiteName);
		
		//System.out.println("第一次出现标题为"+title);
		
		//高峰时间	
		Calendar c = Calendar.getInstance();//日历
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date startDate = format.parse(startTime);//专题开始时间
		Date endDate = format.parse(endTime);//专题结束时间
		
//		c.setTime(date);
		//c.add(c.DATE, 1);
		//Date time = c.getTime();//天数加了一天
		
		//System.out.println("日期c"+c.getTime());
		c.setTime(startDate);
		c.add(c.DATE, -1);
		//用来存放数量和日期
		Integer a = 0;
		String peakdate =null;
		while (true) {
			String s = subjectDao.date2String(startDate);
			c.add(c.DATE, 1);
			Date time = c.getTime();
			if(time.after(endDate)){
				
				break;
			}else{
				startDate=time;
			}
			String date2String = subjectDao.date2String(time);
			//System.out.println("date2String:"+date2String);
			//每天的总数据量
			String sqltotal = subjectDao.splitKeyWord1("select * from tab_iopm_article_info where "+ " (RELEASE_TIME >= '"+date2String+" 00:00:00' and RELEASE_TIME <= '"+date2String+" 23:59:59') and", keyword1,"");
			List<Object> total = es.searchBySql(sqltotal);
			Integer count = (Integer) total.get(0);
			if(count>a){
				a = count;	
				peakdate = date2String;
			}
		}
		//System.out.println("高峰时间"+peakdate);
		
		//计算出这段时间所有信息的总量
		Integer allInfoCount =0;
		String allInfoSql="SELECT * FROM tab_iopm_article_info where  (RELEASE_TIME>='"+startTime+"' and RELEASE_TIME<='"+endTime+"')";
		List<Object> allInfo = es.searchBySql(allInfoSql);
		Integer object = (Integer) allInfo.get(0);
		Integer sumCount = Integer.valueOf((Integer) searchBySqlnews.get(0)) + Integer.valueOf((Integer) searchBySqlweibo.get(0)) + Integer.valueOf((Integer) searchBySqlluntan.get(0))+ Integer.valueOf((Integer) searchBySqlweixin.get(0)) + Integer.valueOf((Integer) searchBySqlvideo.get(0));
		
		float percent =((float)sumCount/object)*100;
		String a1=null;
		if(percent>0.5){
			a1="信息量极大,舆论关注度高。";
		}else if (percent<=0.5 && percent>=0.25) {
			a1="信息量一般,舆论关注度一般。";
		}else {
			a1="信息量小,舆论关注度小。";
		}
		
		String b ="";
		if((Integer)searchBySqlluntan.get(0)==0){
			b="";
		}else{
			b="、论坛主题帖"+searchBySqlluntan.get(0)+"篇";
		}
		
		String d="";
		if((Integer)searchBySqlvideo.get(0)==0){
			d="";
		}else{
			d="、视频"+searchBySqlvideo.get(0)+"个";
		}
		
		String serviceFromPython = subjectDao.serviceFromPython(keyword1);
		String replace = serviceFromPython.replace(",", "、").replace("&", " ");
		/*int lastIndexOf = replace.lastIndexOf("、");
		if(lastIndexOf!=-1){
			replace = replace.substring(0, lastIndexOf-1)+"和"+replace.substring(lastIndexOf+1, replace.length());
		}*/
		
		String yuqing = "该分析报告的监测时间为"+startTime+"至"+endTime+"，监测关键词主要包含“"+replace+"”，监测数据全面覆盖了境内外互联网新闻、博客、论坛、微博和视频等多类型站点。";
		String yuqing2 = "在上述监测时间范围内，网上共计发布相关新闻"+searchBySqlnews.get(0)+"篇、微博"+searchBySqlweibo.get(0)+"篇"+b+""+d+"。首条信息于"+subjectFirst+"发布在"+websiteName+"站点，标题为《"+title+"》。舆论最高峰出现在"+peakdate+"。总体而言，新闻媒体以及微博等社交媒体关于“"+keyword1+"”事件的"+a1+"";
		Map<String, String> map =new HashedMap();
		map.put("Public_sentiment1", yuqing);
		map.put("Public_sentiment2", yuqing2);
		//System.out.println("该分析报告的监测时间为"+startTime+"至"+endTime+"，监测关键词主要包含“"+keyword1+"”，监测数据全面覆盖了境内外互联网新闻、博客、论坛、微博和视频等多类型站点。");
		//System.out.println("在上述监测时间范围内，网上共计发布相关新闻"+searchBySqlnews.get(0)+"篇、微博"+searchBySqlweibo.get(0)+"篇、微信"+searchBySqlweixin.get(0)+"论坛主题帖"+searchBySqlluntan.get(0)+"篇、视频"+searchBySqlvideo.get(0)+"个。首条信息于"+subjectFirst+"发布在"+websiteName+"站点，标题为《"+title+"》。舆论最高峰出现在"+peakdate+"。总体而言，新闻媒体以及微博等社交媒体关于“"+keyword1+"”事件的"+a1+"。");
		this.objectToJson(map);
		return null;
	}
	

	//微博平台传播趋势
	public String weiboCount() throws Exception{
		loadSubjectMessage();
        //String subjectStart = subjectDao.subjectStart(id);//查出专题开始时间
        //String subjectEndTime = subjectDao.subjectEndTime(id);//查处专题结束时间
        ESSearchUtils es = ESSearchUtils.getInstance();
        Calendar c = Calendar.getInstance();//日历
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//日期的格式
        Date startDate = format.parse(startTime);//将String日期格式转换成Date格式
        Date endDate = format.parse(endTime);//将String日期格式转换成Date格式
        
//      c.setTime(date);
        //c.add(c.DATE, 1);
        //Date time = c.getTime();//天数加了一天
        
        //System.out.println("日期c"+c.getTime());
        c.setTime(startDate);
        c.add(c.DATE, -1);
        Map<Object, Object> map = new LinkedHashMap<Object, Object>();
        while (true) {
            String s = subjectDao.date2String(startDate);
            c.add(c.DATE, 1);
            Date time = c.getTime();
            if(time.after(endDate)){
                break;
            }else{
                startDate=time;
            }
            String date2String = subjectDao.date2String(time);
            //System.out.println("date2String:"+date2String);
			String sql = subjectDao.splitKeyWord1("select COUNT(*) from tab_iopm_article_info where (RELEASE_TIME >= '"+date2String+" 00:00:00' and RELEASE_TIME <= '"+date2String+" 23:59:59') and", keyword1, "and INFO_TYPE=3");
			List<Object> searchBySql = es.searchBySql(sql);
			map.put(date2String, searchBySql.get(0));
        }
        //System.out.println(map.toString());
        this.objectToJson(map);
		return null;
	}
	//微信平台传播趋势
	public String weixinCount() throws Exception{
		loadSubjectMessage();
        //String subjectStart = subjectDao.subjectStart(id);//查出专题开始时间
        //String subjectEndTime = subjectDao.subjectEndTime(id);//查处专题结束时间
        ESSearchUtils es = ESSearchUtils.getInstance();
        Calendar c = Calendar.getInstance();//日历
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//日期的格式
        Date startDate = format.parse(startTime);//将String日期格式转换成Date格式
        Date endDate = format.parse(endTime);//将String日期格式转换成Date格式
        
//      c.setTime(date);
        //c.add(c.DATE, 1);
        //Date time = c.getTime();//天数加了一天
        
        //System.out.println("日期c"+c.getTime());
        c.setTime(startDate);
        c.add(c.DATE, -1);
        Map<Object, Object> map = new LinkedHashMap<Object, Object>();
        while (true) {
            String s = subjectDao.date2String(startDate);
            c.add(c.DATE, 1);
            Date time = c.getTime();
            if(time.after(endDate)){
                break;
            }else{
                startDate=time;
            }
            String date2String = subjectDao.date2String(time);
            //System.out.println("date2String:"+date2String);
			String sql = subjectDao.splitKeyWord1("select COUNT(*) from tab_iopm_article_info where (RELEASE_TIME >= '"+date2String+" 00:00:00' and RELEASE_TIME <= '"+date2String+" 23:59:59') and", keyword1, "and INFO_TYPE=2");
			List<Object> searchBySql = es.searchBySql(sql);
			map.put(date2String, searchBySql.get(0));
        }
        //System.out.println(map.toString());
        this.objectToJson(map);
		return null;
	}
	
	//主流媒体排名,以及负面+概述 主流媒体传播趋势排名
	@SuppressWarnings("deprecation")
	public String mainstreamMedia() throws Exception{
		loadSubjectMessage();
		

	   
		String sql1= "(RELEASE_TIME>='"+startTime+"' and RELEASE_TIME<='"+endTime+"') and";
		String httpValue = PropertiesFactory.getValue("es.http.hosts");

		String keyWord = subjectDao.splitKeyWord1("", keyword1, "");//单独分割出keyword1
		
		String utf = URLEncoder.encode(keyWord,"utf-8");
		String encode ="SELECT WEBSITE_NAME,count(WEBSITE_NAME) FROM tab_iopm_article_info WHERE "+sql1;//前半段sql语句
		encode=encode.replace(">", "%3E").replace("<", "%3C").replace(" ", "%20").replace("'", "%27");//替换前半段的字符
		String encode1="and IS_VIP=1 group by WEBSITE_NAME limit 10";//后半段语句
		encode1=encode1.replace(">", "%3E").replace("<", "%3C").replace(" ", "%20").replace("'", "%27");//替换后半段语句的字符
		String test = utf.replace("+", "%20").replace("%28", "(").replace("%29", ")").replace("%3D", "=");//替换keyword1中的字符
		String a =encode+test+encode1;//拼接
		String url ="http://"+httpValue+"/_sql?sql="+a;//拼接上http://+地址
		//System.out.println(url);//输出url
 
		 JSONObject json_VIP = HttpRequest.httpGet(url); //获得页面的数据
        //System.out.println(json_VIP); //打印数据
        //json_VIP = JSONObject.fromObject(json);
        JSONObject jsonObject = (JSONObject) json_VIP.getJSONObject("aggregations").getJSONObject("WEBSITE_NAME");//获得所要数据
		JSONArray object = (JSONArray) jsonObject.get("buckets");//通过获得buckets来得到一个JSONArray
		Map<Object, Object> map = new HashedMap();//用来存放总体数据 包括vip 以及vioAndEmotion 和TEXT
		ArrayList<Object> arrayList = new ArrayList<Object>();//存放循环出来的主流媒体数据
		int count =0;//用来保存主流媒体信息的总数
		for (int i = 0; i < object.size(); i++) {//循环得出结果
			Map object2 = (Map) object.get(i);
			Object webName = object2.get("key");
			Object webCount = object2.get("doc_count");
			Map<Object, Object> valueMap = new HashedMap();
			valueMap.put("WEBSIT_NAME", webName);
			valueMap.put("INFO_COUNT", webCount);
			
			arrayList.add(valueMap);
			int c = (Integer) webCount;
			count=count+c;
			//System.out.println(arrayList.toString());
			//System.out.println(count);
		}
		
		
		//负面
		
		String encode1F="and IS_VIP=1 and EMOTION=2 group by WEBSITE_NAME limit 10";
		encode1F=encode1F.replace(">", "%3E").replace("<", "%3C").replace(" ", "%20").replace("'", "%27");
		
		String b =encode+test+encode1F;
		String urlF ="http://"+httpValue+"/_sql?sql="+b;
		//System.out.println(urlF);
 
		JSONObject json_BadEmotion = HttpRequest.httpGet(urlF); 
        //System.out.println(json_BadEmotion); 
        JSONObject jsonObjectF = (JSONObject) json_BadEmotion.getJSONObject("aggregations").getJSONObject("WEBSITE_NAME");
		JSONArray objectF = (JSONArray) jsonObjectF.get("buckets");
		Map<Object, Object> mapF = new HashedMap();
		ArrayList<Object> arrayListF = new ArrayList<Object>();
		String h ="";//用来拼接字符串中的网址
		for (int i = 0; i < objectF.size(); i++) {
			Map object2 = (Map) objectF.get(i);
			Object webName = object2.get("key");
			Object webCount = object2.get("doc_count");
			Map<Object, Object> valueMap = new HashedMap();
			valueMap.put("WEBSIT_NAME", webName);
			valueMap.put("INFO_COUNT_BAD", webCount);
			//String str="{WEBSIT_NAME:"+webName+",INFO_COUNT_BAD:"+webCount+"}";
			arrayListF.add(valueMap);
			//System.out.println(arrayListF.toString());
		}
		
		for (int j = 0; j < object.size(); j++) {
			Map object2 = (Map) object.get(j);
			Object key = object2.get("key");
			h=h+key+"、";
		}
		if(h.length()>0){
			
			h=h.substring(0, h.length()-1);
		}
		if(object.size()>=3){//判断出如果网站小于3个的时候
			//网站不小于3的情况,直接拿3次,取出前3
			Map k1 = (Map) object.get(0);
			Object kk1 = k1.get("key");
			Map k2 = (Map) object.get(1);
			Object kk2 = k2.get("key");
			Map k3 = (Map) object.get(2);
			Object kk3 = k3.get("key");
			h=kk1+"、"+kk2+"、"+kk3;//拼接主流网站前三结果
		}

		
		
		
		//主流媒体中负面最多的网站名称
		String badName="";
		if(objectF.size()>0){
			Map bad = (Map) objectF.get(0);
			badName = bad.get("key").toString();
			badName ="，其中"+badName+"站点发布负面信息较多";
			//，其中"+badName+"站点发布负面信息较多
		}
		
//		Map object2 = (Map) object.get(1);
//		Object webName = object2.get("key");
		String str ="截至"+endTime+"，"+h+"等主流新闻媒体和门户网站约"+object.size()+"家站点共发布相关新闻信息"+count+"篇"+badName+"。";
		map.put("VIP", arrayList);
		map.put("VIPAndbadEmotion", arrayListF);
		map.put("text", str);
		HttpServletRequest request = ServletActionContext.getRequest();
		request.getSession().setAttribute("VIPAndBadEmotionAndText",map);
		this.objectToJson(map);
		return null;
	}
	
	//微博平台各种排名
	public String weiboTrendRank() throws Exception{
//		HttpServletResponse response = ServletActionContext.getResponse();
//		response.setContentType("application/json;charset=UTF-8");
		loadSubjectMessage();
		ESSearchUtils es = ESSearchUtils.getInstance();
		//String subjectStart = subjectDao.subjectStart(id);
		//String subjectEnd = subjectDao.subjectEndTime(id);

		String sql= " (RELEASE_TIME>='"+startTime+"' and RELEASE_TIME<='"+endTime+"') and";
		
		//最早排名前十
		String earlySql = subjectDao.splitKeyWord1("SELECT RELEASE_TIME,CONTENT,WEBSITE_NAME,COMMENT_COUNT,UP_COUNT,TRANSMIT_COUNT,URL FROM tab_iopm_article_info where  "+sql, keyword1, "   and INFO_TYPE=3 ORDER BY RELEASE_TIME ASC LIMIT 10 ");
		List<Object> early = es.searchBySql(earlySql);
		Object early1 = early.get(1);
		
		//点赞最多前十	
		String upMaxSql = subjectDao.splitKeyWord1("SELECT RELEASE_TIME,CONTENT,WEBSITE_NAME,COMMENT_COUNT,UP_COUNT,TRANSMIT_COUNT,URL FROM tab_iopm_article_info where"+sql, keyword1, "   AND INFO_TYPE=3 ORDER BY UP_COUNT DESC LIMIT 10");
		List<Object> upMax = es.searchBySql(upMaxSql);
		Object upMax1 = upMax.get(1);		
		
		//转发最多前十
		String transmitSql = subjectDao.splitKeyWord1("SELECT RELEASE_TIME,CONTENT,WEBSITE_NAME,COMMENT_COUNT,UP_COUNT,TRANSMIT_COUNT,URL FROM tab_iopm_article_info where "+sql, keyword1, "   AND INFO_TYPE=3 ORDER BY TRANSMIT_COUNT DESC LIMIT 10");
		List<Object> transmit = es.searchBySql(transmitSql);
		Object transmit1 = transmit.get(1);		
		
		//评论最多
		String commentSql = subjectDao.splitKeyWord1("SELECT RELEASE_TIME,CONTENT,WEBSITE_NAME,COMMENT_COUNT,UP_COUNT,TRANSMIT_COUNT,URL FROM tab_iopm_article_info where "+sql, keyword1, "   AND INFO_TYPE=3 ORDER BY COMMENT_COUNT DESC LIMIT 10");
		List<Object> comment = es.searchBySql(commentSql);
		Object comment1 = comment.get(1);	
		
		
		Map map = new HashedMap();
		map.put("early", early1);
		map.put("upMax", upMax1);
		map.put("transmit", transmit1);
		map.put("comment", comment1);
		
		HttpServletRequest request = ServletActionContext.getRequest();
		request.getSession().setAttribute("weiBoRank",map);
		
		this.objectToJson(map);
/*		this.arrayToJson(early);
		this.arrayToJson(upMax);
		this.arrayToJson(transmit);
		this.arrayToJson(comment);*/
		return null;
	}
	
	//微博平台排名分析
	public String weiboTrendRandText() throws Exception{
		//截止至时间
		loadSubjectMessage();

		ESSearchUtils es = ESSearchUtils.getInstance();


		String sql= " (RELEASE_TIME>='"+startTime+"' and RELEASE_TIME<='"+endTime+"') and";
		//相关信息数量
		String sql1 = subjectDao.splitKeyWord1("SELECT count(1) FROM tab_iopm_article_info where "+sql, keyword1, " and INFO_TYPE=3");
		List<Object> weiBoCount = es.searchBySql(sql1);
		Object realWeiBoCount = weiBoCount.get(0);
		//最早的博主和标题
		String firstAuthorSql = subjectDao.splitKeyWord1("SELECT AUTHOR,CONTENT FROM tab_iopm_article_info where "+sql, keyword1, "  AND INFO_TYPE=3 ORDER BY COMMENT_COUNT DESC LIMIT 1");
		List<Object> firstAAndT = es.searchBySql(firstAuthorSql);
		ArrayList<Object> object1 = (ArrayList<Object>) firstAAndT.get(1);

		//参与率最高的博主和标题
		String INTERACTIONsql = subjectDao.splitKeyWord1("SELECT CONTENT,AUTHOR,WEBSITE_NAME FROM tab_iopm_article_info where "+sql, keyword1, "  AND INFO_TYPE=3 ORDER BY INTERACTION_COUNT DESC");
		List<Object> searchINTERACTION = es.searchBySql(INTERACTIONsql);
		ArrayList<Object> object2 = (ArrayList<Object>)searchINTERACTION.get(1);
		Map<String, Object> map =new HashedMap();
		String str1="截至"+endTime+"，新浪微博平台发布相关微博信息共计"+realWeiBoCount+"条";
		if(object1.size()>0&&object2.size()>0){
			
			String object10 =(String) object1.get(0);
			JSONObject json1 = JSONObject.fromObject(object10);
			Object content1 = json1.get("CONTENT");
			Object author1 = json1.get("AUTHOR");
			String replace1 = cutContent((String) content1);
			
			Object object20 = object2.get(0);
			JSONObject json2 = JSONObject.fromObject(object20);
			Object author2 = json2.get("AUTHOR");
			Object content2 = json2.get("CONTENT");
			String replace2 = cutContent((String) content2);			
			str1 = "截至"+endTime+"，新浪微博平台发布相关微博信息共计"+realWeiBoCount+"条，其中"+author1+"博主最早声明【"+replace1+"】，"+author2+"博主发布的【"+replace2+"】用户参与率最高";
			map.put("text", str1);
		}
		
		map.put("text", str1);

		this.objectToJson(map);
		return null;
	}
	
	//微信平台各种排名
	public String weixinTrendRank() throws Exception{
		loadSubjectMessage();
		ESSearchUtils es = ESSearchUtils.getInstance();

		String sql= " (RELEASE_TIME>='"+startTime+"' and RELEASE_TIME<='"+endTime+"') and";
		//最早排名前十	
		String earlySql = subjectDao.splitKeyWord1("SELECT RELEASE_TIME,TITLE,WEBSITE_NAME,UP_COUNT,REVIEW_COUNT,URL FROM tab_iopm_article_info where "+sql, keyword1, "  and INFO_TYPE=2 ORDER BY RELEASE_TIME ASC LIMIT 10 ");
		List<Object> early = es.searchBySql(earlySql);
		Object early1 = early.get(1);
		
		//点赞最多
		String upMaxSql = subjectDao.splitKeyWord1("SELECT RELEASE_TIME,TITLE,WEBSITE_NAME,UP_COUNT,REVIEW_COUNT,URL FROM tab_iopm_article_info where "+sql, keyword1, "  AND INFO_TYPE=2 ORDER BY UP_COUNT DESC LIMIT 10");
		List<Object> upMax = es.searchBySql(upMaxSql);
		Object upMax1 = upMax.get(1);		
		
		//观看最多	
		String commentSql = subjectDao.splitKeyWord1("SELECT RELEASE_TIME,TITLE,WEBSITE_NAME,UP_COUNT,REVIEW_COUNT,URL FROM tab_iopm_article_info where "+sql, keyword1, "  AND INFO_TYPE=2 ORDER BY REVIEW_COUNT DESC LIMIT 10");
		List<Object> comment = es.searchBySql(commentSql);
		Object comment1 = comment.get(1);	
		
		Map<String, Object> map = new HashedMap();
		map.put("early", early1);
		map.put("upMax", upMax1);
		map.put("comment", comment1);
		HttpServletRequest request = ServletActionContext.getRequest();
		request.getSession().setAttribute("weiXinRank",map);
		this.objectToJson(map);
		return null;
	}
	
	//微信平台排名分析
	public String weixinTrendRandText() throws Exception{
		//截止至时间
		loadSubjectMessage();//关键字
		
		//String subjectEnd = subjectDao.subjectEndTime(id);
		ESSearchUtils es = ESSearchUtils.getInstance();


		String subjectStart = subjectDao.subjectStart(id);
		
		String sql= " (RELEASE_TIME>='"+startTime+"' and RELEASE_TIME<='"+endTime+"') and";
		
		//相关信息数量
		String sql1 = subjectDao.splitKeyWord1("SELECT count(1) FROM tab_iopm_article_info where "+sql, keyword1, "  and INFO_TYPE=2");
		List<Object> weiBoCount = es.searchBySql(sql1);
		Object realWeixinCount = weiBoCount.get(0);
		
		//最早的博主和标题
		String firstAuthorSql = subjectDao.splitKeyWord1("SELECT AUTHOR,TITLE FROM tab_iopm_article_info where "+sql, keyword1, "  AND INFO_TYPE=2 ORDER BY RELEASE_TIME ASC LIMIT 1");
		List<Object> firstAAndT = es.searchBySql(firstAuthorSql);
		ArrayList<Object> object1 = (ArrayList<Object>) firstAAndT.get(1);
		
		//参与率最高的博主和标题
		String INTERACTIONsql = subjectDao.splitKeyWord1("SELECT TITLE,AUTHOR,WEBSITE_NAME FROM tab_iopm_article_info where "+sql, keyword1, "  AND INFO_TYPE=2 ORDER BY INTERACTION_COUNT DESC");
		List<Object> searchINTERACTION = es.searchBySql(INTERACTIONsql);
		ArrayList<Object> object2 = (ArrayList<Object>)searchINTERACTION.get(1);
		Map<String, Object> map =new HashedMap();
		String str ="截至"+endTime+"，发布相关微信信息共计"+realWeixinCount+"条";
		if(object1.size()>0&&object2.size()>0){
			String object10 =(String) object1.get(0);
			JSONObject json1 = JSONObject.fromObject(object10);
			Object content1 = json1.get("TITLE").toString().replace("\r\n", "");
			Object author1 = json1.get("AUTHOR");
			
			
			Object object20 = object2.get(0);
			JSONObject json2 = JSONObject.fromObject(object20);
			Object author2 = json2.get("AUTHOR");
			Object content2 = json2.get("TITLE").toString().replace("\r\n", "");
			
			str = "截至"+endTime+"，发布相关微信信息共计"+realWeixinCount+"条，其中"+author1+"公众号最早声明【"+content1+"】，"+author2+"公众号发布的【"+content2+"】用户参与率最高";
			
			map.put("text", str);
		}
		//str = "截至"+subjectEnd+"，发布相关微信信息共计"+realWeixinCount+"条，其中"+author1+"公众号最早声明【"+replace1+"】，"+author2+"公众号发布的【"+replace2+"】用户参与率最高";

		map.put("text", str);

		
		objectToJson(map);
		//System.out.println(str);
		return null;
	}
	
	//影响力+概述
	public String influenceTrends() throws Exception{
		loadSubjectMessage();
		//String subjectStart = subjectDao.subjectStart(id);//查出专题开始时间
		//String subjectEndTime = subjectDao.subjectEndTime(id);//查处专题结束时间
		ESSearchUtils es = ESSearchUtils.getInstance();
		Calendar c = Calendar.getInstance();//日历
		Integer zhuliuCount = 0;//VIP主流媒体总数
		Integer gaizhuxinxiCount = 0;//该主题总数
		Integer zongxinxiCount=0;//总信息量总数
		Integer dayussCount=0;//影响力大于30的天数
		Integer count =0;//总天数
		
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date startDate = format.parse(startTime);
		Date endDate = format.parse(endTime);
		
	//	c.setTime(date);
		//c.add(c.DATE, 1);
		//Date time = c.getTime();//天数加了一天
		
		//System.out.println("日期c"+c.getTime());
		Map<Object, Object> map = new LinkedHashMap<Object, Object>();
		c.setTime(startDate);
		c.add(c.DATE, -1);
		String effectString="";
		while (true) {
			String s = subjectDao.date2String(startDate);
			c.add(c.DATE, 1);
			Date time = c.getTime();
			if(time.after(endDate)){
				break;
			}else{
				startDate=time;
			}
			String date2String = subjectDao.date2String(time);
			//System.out.println("date2String:"+date2String);
			//该主题总信息量
			String sql = subjectDao.splitKeyWord1("select RELEASE_TIME from tab_iopm_article_info where (RELEASE_TIME >= '"+date2String+" 00:00:00' and RELEASE_TIME <= '"+date2String+" 23:59:59') and", keyword1, "");
			//String sql = "select RELEASE_TIME from tab_iopm_article_info where  (RELEASE_TIME >= '"+date2String+" 00:00:00' and RELEASE_TIME <= '"+date2String+" 23:59:59')and (TITLE like '%焦点访谈%' or TITLE like '新闻联播')";
			List<Object> searchBySql = es.searchBySql(sql);
			
			//该主题IS_VIP=1信息量
			String sql1 = subjectDao.splitKeyWord1("select RELEASE_TIME from tab_iopm_article_info where (RELEASE_TIME >= '"+date2String+" 00:00:00' and RELEASE_TIME <= '"+date2String+" 23:59:59') and", keyword1, "and IS_VIP =1");
			//String sql1 ="select RELEASE_TIME from tab_iopm_article_info where  (RELEASE_TIME >= '"+date2String+" 00:00:00' and RELEASE_TIME <= '"+date2String+" 23:59:59')and (TITLE like '%焦点访谈%' or TITLE like '新闻联播') and IS_VIP =1";
			List<Object> searchBySql2 = es.searchBySql(sql1);
			
			//所有主题的总信息量
			String sql2 = "select RELEASE_TIME from tab_iopm_article_info where  (RELEASE_TIME >= '"+date2String+" 00:00:00' and RELEASE_TIME <= '"+date2String+" 23:59:59')";
			List<Object> searchBySql3 = es.searchBySql(sql2);
			
			/*//System.out.println(time);*/
			//System.out.println(date2String+"--该主题总信息量--"+searchBySql.get(0).toString());
			//System.out.println(date2String+"--VIP信息量--"+searchBySql2.get(0).toString());
			//System.out.println(date2String+"--所有主题总信息量--"+searchBySql3.get(0).toString());
			
			//每天影响力
			Integer s1 = (Integer) searchBySql.get(0);//该主题总信息量
			Integer s2 = (Integer) searchBySql2.get(0);//VIP信息量
			Integer s3 = (Integer) searchBySql3.get(0);//所有主题总信息量
			
			//总影响力
			zhuliuCount = zhuliuCount+s2;//VIP主流媒体总数
			gaizhuxinxiCount = gaizhuxinxiCount+s1;//该主题总信息量
			zongxinxiCount = zongxinxiCount+s3;//总信息量总数
			count=count+1;//总天数
			//当天主流媒体占比
			if(s1==0){
				map.put(date2String, 0);
			}else{
				float a1 = ((float)s2/s1);//当天主流媒体占比
				float a2 = ((float)s1/s3);//当天总信息量占比
				float a3 =(float) (((0.5*a1)+(0.5*a2))*100);//每天影响力
				if(a3>30){
					dayussCount =dayussCount++;
				};
				map.put(date2String, a3);
			}
			//System.out.println("结果集为"+map.toString());
	
			
		}
		
		//总影响力
		float b1 = ((float)zhuliuCount/gaizhuxinxiCount);//主流媒体占比
		if(zhuliuCount==0){
			b1=0;
		}
		float b2 = ((float)gaizhuxinxiCount/zongxinxiCount);//总信息量占比
		if(gaizhuxinxiCount==0){
			b2=0;
		}
		float b3 = ((float)dayussCount/count);//一定热度存活时间占比
		if(dayussCount==0){
			b3=0;
		}
		float effect = ((float)((0.5*b1)+(0.3*b2)+(0.2*b3))*100);
		map.put("effect", effect);
		
		//System.out.println("影响力为:"+effect);
		
		if(effect>=0&&effect<=20){
			effectString="传播影响力较小";
		}
		if(effect>=21&&effect<=40){
			effectString="传播影响力一般";
		}
		if(effect>=41&&effect<=60){
			effectString="传播影响力较大";
		}
		if(effect>=61&&effect<=80){
			effectString="传播影响力很大";
		}
		if(effect>=81&&effect<=10){
			effectString="传播影响力一般";
		}
		String selectTitle = subjectDao.selectTitle(id);
		 float b = (float)(Math.round(effect*100))/100;
		String str="“"+selectTitle+"”事件综合影响力评分为"+b+"①。"+effectString+"。";
		//System.out.println(str);
		map.put("text", str);
		this.objectToJson(map);
		/*HttpServletResponse response = ServletActionContext.getResponse();
		response.setContentType("text/html;charset=UTF-8");// 解决中文乱码
		response.getWriter().write(JsonUtils.fromArray(effect));*/
		/*String sql="select RELEASE_TIME from tab_iopm_article_info where  (RELEASE_TIME >= '2017-09-06 00:00:00' and RELEASE_TIME <= '2017-09-06 23:59:59')and (TITLE like '%焦点访谈%' or TITLE like '新闻联播')";
		List<Object> searchBySql = es.searchBySql(sql);*/
		return null;
		}
		
	//阶段演化+概述
	public String consensusByKeyWord() throws Exception{
		loadSubjectMessage();
		//String subjectStart = subjectDao.subjectStart(id);//查出专题开始时间
		//String subjectEndTime = subjectDao.subjectEndTime(id);//查处专题结束时间
		ESSearchUtils es = ESSearchUtils.getInstance();
		Calendar c = Calendar.getInstance();//日历
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date startDate = format.parse(startTime);
		Date endDate = format.parse(endTime);
		
//		c.setTime(date);
		//c.add(c.DATE, 1);
		//Date time = c.getTime();//天数加了一天
		
		//System.out.println("日期c"+c.getTime());
		c.setTime(startDate);
		c.add(c.DATE, -1);
		
		Map<Object, Object> map = new LinkedHashMap<Object, Object>();
		Map<Object, Object> map1 = new LinkedHashMap<Object, Object>();
		Map<Object, Object> map2 = new LinkedHashMap<Object, Object>();
		Map<Object, Object> map3 = new LinkedHashMap<Object, Object>();
		Map<Object, Object> map4 = new LinkedHashMap<Object, Object>();
		Map<Object, Object> map5 = new LinkedHashMap<Object, Object>();
		Map<Object, Object> map6 = new LinkedHashMap<Object, Object>();
		String[] peak1 =null;
		String peak =null;
	
		while (true) {
			String s = subjectDao.date2String(startDate);
			c.add(c.DATE, 1);
			Date time = c.getTime();
			if(time.after(endDate)){
				break;
			}else{
				startDate=time;
			}
			String date2String = subjectDao.date2String(time);
			//System.out.println("date2String:"+date2String);
			//该主题主流媒体的信息量
			String sql = subjectDao.splitKeyWord1("select RELEASE_TIME from tab_iopm_article_info where (RELEASE_TIME >= '"+date2String+" 00:00:00' and RELEASE_TIME <= '"+date2String+" 23:59:59') and", keyword1, "and IS_VIP=1");
			List<Object> searchBySql = es.searchBySql(sql);
			//该专题信息总量
			String sql1 = subjectDao.splitKeyWord1("select RELEASE_TIME from tab_iopm_article_info where ", keyword1, " AND(RELEASE_TIME >= '"+date2String+" 00:00:00' and RELEASE_TIME <= '"+date2String+" 23:59:59')");
			List<Object> searchBySql1 = es.searchBySql(sql1);
			//微博
			String sql2 = subjectDao.splitKeyWord1("select RELEASE_TIME from tab_iopm_article_info where (RELEASE_TIME >= '"+date2String+" 00:00:00' and RELEASE_TIME <= '"+date2String+" 23:59:59') and ",keyword1," AND INFO_TYPE=3");
			List<Object> searchBySql2 = es.searchBySql(sql2);
			//微信
			String sql3 = subjectDao.splitKeyWord1("select RELEASE_TIME from tab_iopm_article_info where (RELEASE_TIME >= '"+date2String+" 00:00:00' and RELEASE_TIME <= '"+date2String+" 23:59:59') and ",keyword1," AND INFO_TYPE=2");
			List<Object> searchBySql3 = es.searchBySql(sql3);
			//新闻
			String sql4 = subjectDao.splitKeyWord1("select RELEASE_TIME from tab_iopm_article_info where (RELEASE_TIME >= '"+date2String+" 00:00:00' and RELEASE_TIME <= '"+date2String+" 23:59:59') and ",keyword1," AND INFO_TYPE=1");
			List<Object> searchBySql4 = es.searchBySql(sql4);
			//app
			String sql5 = subjectDao.splitKeyWord1("select RELEASE_TIME from tab_iopm_article_info where (RELEASE_TIME >= '"+date2String+" 00:00:00' and RELEASE_TIME <= '"+date2String+" 23:59:59') and ",keyword1," AND INFO_TYPE=7");
			List<Object> searchBySql5 = es.searchBySql(sql5);
			//视频
			String sql6 = subjectDao.splitKeyWord1("select RELEASE_TIME from tab_iopm_article_info where (RELEASE_TIME >= '"+date2String+" 00:00:00' and RELEASE_TIME <= '"+date2String+" 23:59:59') and ",keyword1," AND INFO_TYPE=8");
			List<Object> searchBySql6 = es.searchBySql(sql6);
			
			map.put(date2String, searchBySql.get(0));
			map1.put(date2String, searchBySql1.get(0));//总信息量
			map2.put(date2String, searchBySql2.get(0));
			map3.put(date2String, searchBySql3.get(0));
			map4.put(date2String, searchBySql4.get(0));
			map5.put(date2String, searchBySql5.get(0));
			map6.put(date2String, searchBySql6.get(0));
		}
		//System.out.println(map);
		//System.out.println(map1);
		//System.out.println(map2);
		//System.out.println(map3);
		//System.out.println(map4);
		//System.out.println(map5);
		//System.out.println(map6);
		

		
		//计算峰值
		String peak2 =subjectDao.peak(map1);
		
		Map<Object,Object> mapppp = new LinkedHashMap<Object, Object>();
		mapppp.put("vipCount", map);
		mapppp.put("weiboCount", map2);
		mapppp.put("allCount", map1);
		mapppp.put("weixinCount", map3);
		mapppp.put("xinwenCount", map4);
		mapppp.put("appCount", map5);
		mapppp.put("shipinCount", map6);
		String selectTitle = subjectDao.selectTitle(id);
		String str = "如图所示，“"+selectTitle+"”事件的发展"+peak2+"";
		//System.out.println(mapppp);
		mapppp.put("text", str);
		this.objectToJson(mapppp);
		return null;
	}
	
	
	//主流媒体传播趋势
	public String evenStage() throws Exception{
		loadSubjectMessage();
		//String subjectStart = subjectDao.subjectStart(id);//查出专题开始时间
		//String subjectEndTime = subjectDao.subjectEndTime(id);//查处专题结束时间
		ESSearchUtils es = ESSearchUtils.getInstance();
		Calendar c = Calendar.getInstance();//日历
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date startDate = format.parse(startTime);
		Date endDate = format.parse(endTime);
		
//		c.setTime(date);
		//c.add(c.DATE, 1);
		//Date time = c.getTime();//天数加了一天
		
		//System.out.println("日期c"+c.getTime());
		c.setTime(startDate);
		c.add(c.DATE, -1);
		
		Map<Object, Object> map = new LinkedHashMap<Object, Object>();
		Map<Object, Object> map1 = new LinkedHashMap<Object, Object>();
		
		
		while (true) {
			String s = subjectDao.date2String(startDate);
			c.add(c.DATE, 1);
			Date time = c.getTime();
			if(time.after(endDate)){
				break;
			}else{
				startDate=time;
			}
			String date2String = subjectDao.date2String(time);
			//System.out.println("date2String:"+date2String);
			//该主题主流媒体的信息量
			String sql = subjectDao.splitKeyWord1("select RELEASE_TIME from tab_iopm_article_info where  (RELEASE_TIME >= '"+date2String+" 00:00:00' and RELEASE_TIME <= '"+date2String+" 23:59:59') and", keyword1, " and IS_VIP=1");
			List<Object> searchBySql = es.searchBySql(sql);
			//该专题主流中负面信息量
			String sql1 = subjectDao.splitKeyWord1("select RELEASE_TIME from tab_iopm_article_info where (RELEASE_TIME >= '"+date2String+" 00:00:00' and RELEASE_TIME <= '"+date2String+" 23:59:59') and", keyword1, "and IS_VIP=1 and EMOTION=2");
			List<Object> searchBySql1 = es.searchBySql(sql1);
			
			
			map.put(date2String, searchBySql.get(0));
			map1.put(date2String, searchBySql1.get(0));

		}
		//System.out.println(map);

		Map<Object,Object> mapppp = new LinkedHashMap<Object, Object>();
		mapppp.put("vipCount", map);
		mapppp.put("emotion", map1);
		
		
		
		
		//System.out.println(mapppp);
		this.objectToJson(mapppp);
		return null;
	}
	
	
	//标题和时间
	public String titleAndTimeById() throws Exception{
		loadSubjectMessage();
		String title = subjectDao.selectTitle(id);
		//String subjectStart = subjectDao.subjectStart(id);
		String subjectEndTime = subjectDao.subjectEndTime(id);
		//System.out.println("标题:"+title+"---开始时间:"+startTime+"-----结束时间:"+subjectEndTime);
		Map<String, String> map = new HashedMap();
		map.put("title", title);
		map.put("startTime", startTime);
		map.put("endTime", subjectEndTime);
		map.put("id", String.valueOf(id));
		this.objectToJson(map);
		return null;
	}
	
	//正面观点网民意见描述
	public String positiveView()throws Exception{
		loadSubjectMessage();
		ESSearchUtils es = ESSearchUtils.getInstance();
		


		//String subjectStart = subjectDao.subjectStart(id);
		//String subjectEnd = subjectDao.subjectEndTime(id);
		String sql1=  " (RELEASE_TIME>='"+startTime+"' and RELEASE_TIME<='"+endTime+"') and";
		//String splitKeyWord1 = subjectDao.splitKeyWord1(keyword1);
		//String sql ="select TITLE,CONTENT from tab_iopm_article_info where "+sql1+" TITLE LIKE"+splitKeyWord1+"AND EMOTION=1 order by INTERACTION_COUNT desc limit 3";
		String sql = subjectDao.splitKeyWord1AndNoContent("select TITLE,CONTENT from tab_iopm_article_info where "+sql1+"", keyword1, "AND EMOTION=1 order by INTERACTION_COUNT desc limit 3");
		List<Object> searchBySql = es.searchBySql(sql);
		ArrayList<Object> positive1= (ArrayList<Object>) searchBySql.get(1);
		 String str = "";
		 Map<Object, Object> map =new LinkedHashMap<Object, Object>();//用来存放key为TITLE,value为CONTETN.以此去重
		    if(positive1.size()>0){
		    	if(positive1.size()==1){
		    		Object positive10 = positive1.get(0);//排名第一的
		    	    JSONObject json1 = JSONObject.fromObject(positive10);
		    	    String title1 = json1.get("TITLE").toString();//第一个的标题
		    	    String content1 = json1.get("CONTENT").toString();//第一个的内容
		    	    content1 = cutContent(content1);
		    	    str = "正面观点包含“"+title1.replace("\r", "").replace("\n", "")+"”等代表性观点，其中代表性微博/网帖主要有“"+content1.replace("\r\n", "")+"”。 ";
		    	}
		    	if(positive1.size()==2){
		    		Object positive10 = positive1.get(0);//排名第一的
		    	    JSONObject json1 = JSONObject.fromObject(positive10);
		    	    String title1 = json1.get("TITLE").toString();//第一个的标题
		    	    String content1 = json1.get("CONTENT").toString();//第一个的内容
		    	    content1 = cutContent(content1);
		    	    
		    	    Object positive20 = positive1.get(1);//排名第二的
		    	    JSONObject json2 = JSONObject.fromObject(positive20);
		    	    String title2 = json2.get("TITLE").toString();//第二个的标题
		    	    String content2 = json2.get("CONTENT").toString();//第二个的内容
		    	    content2 = cutContent(content2);	    	    String a = "";
		    	    String b= "";
		    	    map.put(title2, content2);
		    	    map.put(title1, content1);
		    	    //System.out.println("通过Map.entrySet遍历key和value");
		    	    for (Entry<Object, Object> entry : map.entrySet()) {
		    	    	a=a+entry.getKey()+"”、“";//拼接TITLE
		    	    	b=b+entry.getValue()+"”、“";//拼接CONTENT
		    	    }
		    	    a = a.substring(0, a.length()-2).replace("\r", "").replace("\n", "");//去掉最后多余的标点符号
		    	    b=b.substring(0, b.length()-2).replace("\r", "").replace("\n", "");//去掉最后多余的标点符号
		    	   str = "正面观点包含：“"+a+" 等代表性观点，其中代表性微博/网帖主要有“"+b+"。 ";
		    	}
		    	if(positive1.size()>2){
		    		Object positive10 = positive1.get(0);//排名第一的
		    	    JSONObject json1 = JSONObject.fromObject(positive10);
		    	    String title1 = json1.get("TITLE").toString();//第一个的标题
		    	    String content1 = json1.get("CONTENT").toString();//第一个的内容
		    	    content1 = cutContent(content1);
		    	    
		    	    Object positive20 = positive1.get(1);//排名第二的
		    	    JSONObject json2 = JSONObject.fromObject(positive20);
		    	    String title2 = json2.get("TITLE").toString();//第二个的标题
		    	    String content2 = json2.get("CONTENT").toString();//第二个的内容
		    	    content2 = cutContent(content2);
		    	    
		    	    Object positive30 = positive1.get(2);//排名第三的
		    	    JSONObject json3 = JSONObject.fromObject(positive30);
		    	    String title3 = json3.get("TITLE").toString();//第三个的标题
		    	    String content3 = json3.get("CONTENT").toString();//第三个的内容
		    	    content3 = cutContent(content3);
		    	    map.put(title3, content3);
		    	    map.put(title2, content2);
		    	    map.put(title1, content1);
		    	    String a = "";
		    	    String b= "";
		    	    //System.out.println("通过Map.entrySet遍历key和value");
		    	    for (Entry<Object, Object> entry : map.entrySet()) {
		    	    	a=a+entry.getKey()+"”、“";
		    	    	b=b+entry.getValue()+"”、“";
		    	    }
		    	    a = a.substring(0, a.length()-2).replace("\r", "").replace("\n", "");
		    	    b=b.substring(0, b.length()-2).replace("\r", "").replace("\n", "");
		    	     str = "正面观点包含：“"+a+" 等代表性观点，其中代表性微博/网帖主要有“"+b+"。 ";
		    	}
	
		    }
		    //System.out.println(str);
		    Map<String, String> string = new LinkedHashMap<String, String>();
		    string.put("text", str);
		this.objectToJson(string);
		return null;
	}
	//负面观点网民意见描述
	public String negativeView()throws Exception{
		loadSubjectMessage();
	    ESSearchUtils es = ESSearchUtils.getInstance();
	    //String subjectStart = subjectDao.subjectStart(id);
		//String subjectEnd = subjectDao.subjectEndTime(id);
		String sql1=  " (RELEASE_TIME>='"+startTime+"' and RELEASE_TIME<='"+endTime+"') and";
		//String sql ="select TITLE,CONTENT from tab_iopm_article_info where "+sql1+" TITLE LIKE"+splitKeyWord1+"AND EMOTION=2 order by INTERACTION_COUNT desc limit 3";
		String sql = subjectDao.splitKeyWord1AndNoContent("select TITLE,CONTENT from tab_iopm_article_info where "+sql1+"", keyword1, "AND EMOTION=2 order by INTERACTION_COUNT desc limit 3");
		List<Object> searchBySql = es.searchBySql(sql);
	    
	    ArrayList<Object> positive1= (ArrayList<Object>) searchBySql.get(1);
	    Map<Object, Object> map =new LinkedHashMap<Object, Object>();
	    String str = "";
	    if(positive1.size()>0){
	    	if(positive1.size()==1){
	    		Object positive10 = positive1.get(0);//排名第一的
	    	    JSONObject json1 = JSONObject.fromObject(positive10);
	    	    String title1 = json1.get("TITLE").toString();//第一个的标题
	    	    String content1 = json1.get("CONTENT").toString();//第一个的内容
	    	    content1 = cutContent(content1);
	    	    str = "负面观点包含“"+title1.replace("\r", "").replace("\n", "")+"”等代表性观点，其中代表性微博/网帖主要有“"+content1.replace("\r\n", "")+"”。 ";
	    	}
	    	if(positive1.size()==2){
	    		Object positive10 = positive1.get(0);//排名第一的
	    	    JSONObject json1 = JSONObject.fromObject(positive10);
	    	    String title1 = json1.get("TITLE").toString();//第一个的标题
	    	    String content1 = json1.get("CONTENT").toString();//第一个的内容
	    	    content1 = cutContent(content1);
	    	    
	    	    Object positive20 = positive1.get(1);//排名第二的
	    	    JSONObject json2 = JSONObject.fromObject(positive20);
	    	    String title2 = json2.get("TITLE").toString();//第二个的标题
	    	    String content2 = json2.get("CONTENT").toString();//第二个的内容
	    	    content2 = cutContent(content2);
	    	    String a = "";
	    	    String b= "";
	    	    map.put(title2, content2);
	    	    map.put(title1, content1);
	    	    //System.out.println("通过Map.entrySet遍历key和value");
	    	    for (Entry<Object, Object> entry : map.entrySet()) {
	    	    	a=a+entry.getKey()+"”、“";
	    	    	b=b+entry.getValue()+"”、“";
	    	    }
	    	    a = a.substring(0, a.length()-2).replace("\r", "").replace("\n", "");
	    	    b=b.substring(0, b.length()-2).replace("\r", "").replace("\n", "");
	    	   str = "负面观点包含：“"+a+" 等代表性观点，其中代表性微博/网帖主要有“"+b+"。 ";
	    	}
	    	if(positive1.size()>2){
	    		Object positive10 = positive1.get(0);//排名第一的
	    	    JSONObject json1 = JSONObject.fromObject(positive10);
	    	    String title1 = json1.get("TITLE").toString();//第一个的标题
	    	    String content1 = json1.get("CONTENT").toString();//第一个的内容
	    	    content1 = cutContent(content1);
	    	    
	    	    Object positive20 = positive1.get(1);//排名第二的
	    	    JSONObject json2 = JSONObject.fromObject(positive20);
	    	    String title2 = json2.get("TITLE").toString();//第二个的标题
	    	    String content2 = json2.get("CONTENT").toString();//第二个的内容
	    	    content2 = cutContent(content2);
	    	    
	    	    Object positive30 = positive1.get(2);//排名第三的
	    	    JSONObject json3 = JSONObject.fromObject(positive30);
	    	    String title3 = json3.get("TITLE").toString();//第三个的标题
	    	    String content3 = json3.get("CONTENT").toString();//第三个的内容
	    	    content3 = cutContent(content3);
	    	    map.put(title3, content3);
	    	    map.put(title2, content2);
	    	    map.put(title1, content1);
	    	    String a = "";
	    	    String b= "";
	    	    //System.out.println("通过Map.entrySet遍历key和value");
	    	    for (Entry<Object, Object> entry : map.entrySet()) {
	    	    	a=a+entry.getKey()+"”、“";
	    	    	b=b+entry.getValue()+"”、“";
	    	    }
	    	    a = a.substring(0, a.length()-2).replace("\r", "").replace("\n", "");
	    	    b=b.substring(0, b.length()-2).replace("\r", "").replace("\n", "");
	    	     str = "负面观点包含：“"+a+" 等代表性观点，其中代表性微博/网帖主要有“"+b+"。 ";
	    	}
	    }
	    
	    //System.out.println(str);
	    Map<String, String> string = new LinkedHashMap<String, String>();
	    string.put("text", str);
	this.objectToJson(string);
	return null;
		}
	
		//中立观点网民意见描述
	public String neutralView()throws Exception{
		loadSubjectMessage();
	    ESSearchUtils es = ESSearchUtils.getInstance();
	    
	    //String subjectStart = subjectDao.subjectStart(id);
		//String subjectEnd = subjectDao.subjectEndTime(id);
		String sql1=  " (RELEASE_TIME>='"+startTime+"' and RELEASE_TIME<='"+endTime+"') and";
		//String sql ="select TITLE,CONTENT from tab_iopm_article_info where "+sql1+" TITLE LIKE"+splitKeyWord1+"AND EMOTION=3 order by INTERACTION_COUNT desc limit 3";
		String sql = subjectDao.splitKeyWord1AndNoContent("select TITLE,CONTENT from tab_iopm_article_info where "+sql1+"", keyword1, "AND EMOTION=3 order by INTERACTION_COUNT desc limit 3");
		List<Object> searchBySql = es.searchBySql(sql);

	    ArrayList<Object> positive1= (ArrayList<Object>) searchBySql.get(1);
	    Map<Object, Object> map =new LinkedHashMap<Object, Object>();
	    String str = "";
	    if(positive1.size()>0){
	    	if(positive1.size()==1){
	    		Object positive10 = positive1.get(0);//排名第一的
	    	    JSONObject json1 = JSONObject.fromObject(positive10);
	    	    String title1 = json1.get("TITLE").toString();//第一个的标题
	    	    String content1 =json1.get("CONTENT").toString();//第一个的内容
	    	    content1 = cutContent(content1);
	    	    str = "中立观点包含“"+title1.replace("\r", "").replace("\n", "")+"”等代表性观点，其中代表性微博/网帖主要有“"+content1.replace("\r\n", "")+"”。 ";
	    	}
	    	if(positive1.size()==2){
	    		Object positive10 = positive1.get(0);//排名第一的
	    	    JSONObject json1 = JSONObject.fromObject(positive10);
	    	    String title1 = json1.get("TITLE").toString();//第一个的标题
	    	    String content1 = json1.get("CONTENT").toString();//第一个的内容
	    	    content1 = cutContent(content1);
	    	    
	    	    Object positive20 = positive1.get(1);//排名第二的
	    	    JSONObject json2 = JSONObject.fromObject(positive20);
	    	    String title2 =json2.get("TITLE").toString();//第二个的标题
	    	    String content2 =json2.get("CONTENT").toString();//第二个的内容
	    	    content2 = cutContent(content2);
	    	    String a = "";
	    	    String b= "";
	    	    map.put(title2, content2);
	    	    map.put(title1, content1);
	    	    //System.out.println("通过Map.entrySet遍历key和value");
	    	    for (Entry<Object, Object> entry : map.entrySet()) {
	    	    	a=a+entry.getKey()+"”、“";
	    	    	b=b+entry.getValue()+"”、“";
	    	    }
	    	    a = a.substring(0, a.length()-2).replace("\r", "").replace("\n", "");
	    	    b=b.substring(0, b.length()-2).replace("\r", "").replace("\n", "");
	    	   str = "中立观点包含：“"+a+" 等代表性观点，其中代表性微博/网帖主要有“"+b+"。 ";
	    	}
	    	if(positive1.size()>2){
	    		Object positive10 = positive1.get(0);//排名第一的
	    	    JSONObject json1 = JSONObject.fromObject(positive10);
	    	    String title1 = json1.get("TITLE").toString().replace("\r", "").replace("\n", "");//第一个的标题
	    	   // String content1 = (String) json1.get("CONTENT");//第一个的内容
	    	    String content1 = json1.get("CONTENT").toString();
	    	    content1 = cutContent(content1);
	    	    
	    	    Object positive20 = positive1.get(1);//排名第二的
	    	    JSONObject json2 = JSONObject.fromObject(positive20);
	    	    String title2 = json2.get("TITLE").toString().replace("\r", "").replace("\n", "");//第二个的标题
	    	    String content2 =  json2.get("CONTENT").toString();//第二个的内容

	    	    content2 = cutContent(content2);
	    	    
	    	    Object positive30 = positive1.get(2);//排名第三的
	    	    JSONObject json3 = JSONObject.fromObject(positive30);
	    	    String title3 = json3.get("TITLE").toString().replace("\r", "").replace("\n", "");//第三个的标题
	    	    String content3 = json3.get("CONTENT").toString();//第三个的内容
	    	    content3 = cutContent(content3);
	    	    
	    	    map.put(title3, content3);
	    	    map.put(title2, content2);
	    	    map.put(title1, content1);
	    	    String a = "";
	    	    String b= "";
	    	    //System.out.println("通过Map.entrySet遍历key和value");
	    	    for (Entry<Object, Object> entry : map.entrySet()) {
	    	    	a=a+entry.getKey()+"”、“";
	    	    	b=b+entry.getValue()+"”、“";
	    	     ////System.out.println("key= " + entry.getKey() + " and value= " + entry.getValue());
	    	    }
	    	    a = a.substring(0, a.length()-2).replace("\r", "").replace("\n", "");
	    	    b=b.substring(0, b.length()-2).replace("\r", "").replace("\n", "");
	    	     str = "中立观点包含：“"+a+" 等代表性观点，其中代表性微博/网帖主要有“"+b+"。 ";
	    	}
	    }
	    
	    //System.out.println(str);
	    Map<String, String> string = new LinkedHashMap<String, String>();
	    string.put("text", str);
	this.objectToJson(string);
	return null;
		}
	
	//综合研判
	public String comprehensive()throws Exception{
		loadSubjectMessage();
		String subject = subjectDao.selectTitle(id);
		ESSearchUtils es = ESSearchUtils.getInstance();
		//String subjectStart = subjectDao.subjectStart(id);
		//String subjectEnd = subjectDao.subjectEndTime(id);
		String sql1=  " (RELEASE_TIME>='"+startTime+"' and RELEASE_TIME<='"+endTime+"') and";
		//新闻数量	
		String newsSql1 = subjectDao.splitKeyWord1("select count(INFO_TYPE) from tab_iopm_article_info where "+sql1, keyword1, "and INFO_TYPE =1");
		List<Object> searchBySqlnews = es.searchBySql(newsSql1);
		//System.out.println("新闻数量为"+searchBySqlnews.get(0));
		
		
		//微博数量
		String weiboSql1 = subjectDao.splitKeyWord1("select count(INFO_TYPE) from tab_iopm_article_info where "+sql1, keyword1, "and INFO_TYPE =3");
		List<Object> searchBySqlweibo = es.searchBySql(weiboSql1);
		//System.out.println("微博数量为"+searchBySqlweibo.get(0));
		
		//论坛数量
		String luntanSql1 = subjectDao.splitKeyWord1("select count(INFO_TYPE) from tab_iopm_article_info where "+sql1, keyword1, "and INFO_TYPE =4");
		List<Object> searchBySqlluntan = es.searchBySql(luntanSql1);
		//System.out.println("论坛数量为"+searchBySqlluntan.get(0));
		
		//视频
		String videoSql1 = subjectDao.splitKeyWord1("select count(INFO_TYPE) from tab_iopm_article_info where "+sql1, keyword1, "and INFO_TYPE =8");
		List<Object> searchBySqlvideo = es.searchBySql(videoSql1);
		//System.out.println("视频数量为"+searchBySqlvideo.get(0));
		
		//微信
		String weixinSql1 = subjectDao.splitKeyWord1("select count(INFO_TYPE) from tab_iopm_article_info where "+sql1, keyword1, "and INFO_TYPE =2");
		List<Object> searchBySqlweixin = es.searchBySql(weixinSql1);
		//System.out.println("微信数量为"+searchBySqlweixin.get(0));
		String a1=null;
		Integer sumCount = Integer.valueOf((Integer) searchBySqlnews.get(0)) + Integer.valueOf((Integer) searchBySqlweibo.get(0)) + Integer.valueOf((Integer) searchBySqlluntan.get(0))+ Integer.valueOf((Integer) searchBySqlweixin.get(0)) + Integer.valueOf((Integer) searchBySqlvideo.get(0));
		if(sumCount>1000){
			a1="信息量极大,舆论关注度高,";
		}else if (sumCount<=1000 && sumCount>=100) {
			a1="信息量一般,舆论关注度一般,";
		}else {
			a1="信息量小,舆论关注度小,";
		}
		
		String keyWord1 = subjectDao.splitKeyWord1("", keyword1, "");
		String sql= " (RELEASE_TIME>='"+startTime+"' and RELEASE_TIME<='"+endTime+"') and";
		String httpValue = PropertiesFactory.getValue("es.http.hosts");
		String json = subjectDao.URLSql(httpValue, "SELECT COUNT(EMOTION),EMOTION FROM tab_iopm_article_info WHERE"+sql, keyWord1, "GROUP BY EMOTION");
		JSONObject jsonAll = JSONObject.fromObject(json);
        JSONObject jsonObject = (JSONObject) jsonAll.getJSONObject("aggregations").getJSONObject("EMOTION");//获得所要数据
		JSONArray object = (JSONArray) jsonObject.get("buckets");//通过获得buckets来得到一个JSONArray
		int a = 0;//正面
		int b = 0;//负面
		int c = 0;//中立
		int size2 = object.size();
		if(size2==1){
			JSONObject key1 = (JSONObject) object.get(0);//获得key=1的
			int object2 = Integer.valueOf(key1.get("key").toString());
			if(object2==1){
				a = Integer.valueOf( key1.get("doc_count").toString());
			}else if(object2==2){
				b = Integer.valueOf( key1.get("doc_count").toString());
			}else if(object2==3){
				c = Integer.valueOf( key1.get("doc_count").toString());
			}

		}
		if(size2==2){
			JSONObject key1 = (JSONObject) object.get(0);//获得key=1的
			JSONObject key2 = (JSONObject) object.get(1);//获得key=2的
			int object2 = Integer.valueOf(key1.get("key").toString());
			int object3 = Integer.valueOf(key2.get("key").toString());
			if(object2==1){
				a = Integer.valueOf( key1.get("doc_count").toString());
			}
			if(object2==2){
				b = Integer.valueOf( key1.get("doc_count").toString());
			}
			if(object2==3){
				c = Integer.valueOf( key1.get("doc_count").toString());
			}
			if(object3==1){
				a = Integer.valueOf( key2.get("doc_count").toString());
			}
			if(object3==2){
				b = Integer.valueOf( key2.get("doc_count").toString());
			}
			if(object3==3){
				c = Integer.valueOf( key2.get("doc_count").toString());
			}

		}
		

		if(size2>2){
			JSONObject key1 = (JSONObject) object.get(0);//获得key=1的 正面
			JSONObject key2 = (JSONObject) object.get(1);//获得key=2的 负面
			JSONObject key3 = (JSONObject) object.get(2);//获得key=3的 中立
			 a = (Integer) key1.get("doc_count");//正面
			 b = (Integer) key2.get("doc_count");//负面
			 c = (Integer) key3.get("doc_count");//中立
		}
		String type = "";
		
				if (a>=b&&a>=c) {
					if(b>c){
						type="。正面及负面意见占主流,中立意见也占有一定比例";
					}
					if(c>=b){
						type="。正面及中立意见占主流,负面意见也占有一定比例";
					}
				}else if (b>=a&&b>=c) {
					if(a>=c){
						type="。负面及正面意见占主流,中立意见也占有一定比例";
					}
					if(c>a){
						type ="。负面及中立意见占主流,正面意见也占有一定比例";
					}

				}else if (c>=a&&c>=b) {
					if(a>=b){
						type="。中立及正面意见占主流,负面意见也占有一定比例";
					}
					if(b>a){
						type="。中立以及负面意见占主流,正面意见也占有一定比例";
					}
				}
				String text1=startTime+"至"+endTime+"这段时间内，"+subject+"事件,  "+a1;
				String text2=type+"。";

			text1=startTime+"至"+endTime+"这段时间内，"+subject+"事件,  "+a1;
			Map<Object, Object> map = new HashedMap();//用来存放总体数据	
			map.put("text1", text1);
			map.put("text2", text2);
		this.objectToJson(map);
		return null;
	}
	
	
	
	public String saveId() throws Exception{
		setId(id);
		
		return null;
	}
	
	
	
	public Long getId() {
		return id;
	}


	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getStartTime() {
		return startTime;
	}
	
	public void setStartTime(String startTime) {
		this.startTime = startTime;
	}
	
	public String getEndTime() {
		return endTime;
	}
	
	public void setEndTime(String endTime) {
		this.endTime = endTime;
	}
	
	public String getKeyword1() {
		return keyword1;
	}
	
	public void setKeyword1(String keyword1) {
		this.keyword1 = keyword1;
	}
	
	public String getKeyword2() {
		return keyword2;
	}
	
	public void setKeyword2(String keyword2) {
		this.keyword2 = keyword2;
	}
	public String getSubjectName() {
		return subjectName;
	}


	public void setSubjectName(String subjectName) {
		this.subjectName = subjectName;
	}
	

	public List sort(List list, Comparator cmp){
		Collections.sort(list, cmp);
		return list;
	}
	public void smbPut(String remoteUrl, String localFilePath)    
    {    
        InputStream in = null;    
        OutputStream out = null;    
        try    
        {    
            File localFile = new File(localFilePath);    
    
            String fileName = localFile.getName();    
            SmbFile remoteFile = new SmbFile(remoteUrl+"/"+ fileName);    
            in = new BufferedInputStream(new FileInputStream(localFile));    
            out = new BufferedOutputStream(new SmbFileOutputStream(remoteFile));    
            byte[] buffer = new byte[1024];    
            while (in.read(buffer) != -1)    
            {    
                out.write(buffer);    
                buffer = new byte[1024];    
            }    
        }    
        catch (Exception e)    
        {    
            e.printStackTrace();    
        }    
        finally    
        {    
            try    
            {    
                out.close();    
                in.close();    
            }    
            catch (IOException e)    
            {    
                e.printStackTrace();    
            }    
        }    
    }
	
	
}
