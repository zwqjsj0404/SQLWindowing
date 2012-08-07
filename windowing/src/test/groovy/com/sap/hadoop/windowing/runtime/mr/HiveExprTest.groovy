package com.sap.hadoop.windowing.runtime.mr

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.junit.BeforeClass;
import org.junit.Test;

import com.sap.hadoop.HiveUtils;
import com.sap.hadoop.windowing.MRBaseTest;
import com.sap.hadoop.query2.WindowingTypeCheckProcFactory;
import com.sap.hadoop.query2.specification.QuerySpec;
import com.sap.hadoop.windowing.io.IOUtils;
import com.sap.hadoop.windowing.io.WindowingInput;

import org.apache.hadoop.io.Writable;
import org.apache.hadoop.hive.conf.HiveConf;

import org.apache.hadoop.hive.serde2.Deserializer;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.ql.lib.DefaultGraphWalker;
import org.apache.hadoop.hive.ql.lib.DefaultRuleDispatcher;
import org.apache.hadoop.hive.ql.lib.Dispatcher;
import org.apache.hadoop.hive.ql.lib.GraphWalker;
import org.apache.hadoop.hive.ql.lib.Node;
import org.apache.hadoop.hive.ql.lib.NodeProcessor;
import org.apache.hadoop.hive.ql.lib.Rule;
import org.apache.hadoop.hive.ql.lib.RuleRegExp;
import org.apache.hadoop.hive.ql.parse.RowResolver;

import com.sap.hadoop.windowing.MRBaseTest;
import com.sap.hadoop.windowing.WindowingException;

import com.sap.hadoop.windowing.parser.*;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeNodeStream;
import org.antlr.runtime.tree.TreeAdaptor;
import org.antlr.runtime.tree.CommonTreeAdaptor;
import org.antlr.runtime.Token;

import org.apache.hadoop.hive.ql.parse.ASTNode;

import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.apache.hadoop.hive.ql.parse.TypeCheckProcFactory;
import org.apache.hadoop.hive.ql.parse.TypeCheckCtx;
import org.apache.hadoop.hive.ql.parse.UnparseTranslator;
import org.apache.hadoop.hive.ql.plan.ExprNodeDesc;

import org.apache.hadoop.hive.ql.plan.SelectDesc

import com.sap.hadoop.windowing.runtime.mr.HiveRTTest.SelectOp

class HiveExprTest extends MRBaseTest
{

	static RowResolver rr;
	static WindowingInput wIn;
	static Deserializer deS;
	static ObjectInspector inoI;
	static Writable w;
	static TypeCheckCtx typeChkCtx;
	
	@BeforeClass
	public static void setupClass()
	{
		MRBaseTest.setupClass();
		wIn = IOUtils.createTableWindowingInput(null, "part", wshell.cfg)
		deS = wIn.getDeserializer()
		inoI = deS.getObjectInspector()
		w = wIn.createRow();
		rr = HiveUtils.getRowResolver(null, "part", "part", wshell.cfg)
		typeChkCtx = new TypeCheckCtx(rr);
		typeChkCtx.setUnparseTranslator(new UnparseTranslator());
	}
	
	@Test
	void test1()
	{
		ASTNode expr = build("p_name");
		HashMap<Node, Object> map = WindowingTypeCheckProcFactory.genExprNode(expr, typeChkCtx)
		
		ExprNodeDesc node = map.get(expr)
		ArrayList<ExprNodeDesc> cols = [node];
		ArrayList<String> aliases = ["c1"]
		SelectDesc selectDesc = new SelectDesc(cols, aliases, false)
		SelectOp select = new SelectOp()
		select.initialize(selectDesc, inoI)
		
		
		while( wIn.next(w) != -1)
		{
			Object r = deS.deserialize(w)
			select.process(r)
			println select.output
		}
	}
	
	@Test
	void testStringExprs()
	{
		ArrayList<ASTNode> exprs = [
			build("substr(p_name, 1,5)"),
			build("concat(substr(p_name, 1,5), '--')"),
			build("lpad(concat(substr(p_name, 1,5), '--'),10, ' ')"),
			build("lpad(concat(substr(p_name, 1,5), '--'),10, ' ') like '%e%')"),
			build("upper(lpad(concat(substr(p_name, 1,5), '--'),10, ' '))")
		]
		
		HashMap<Node, Object> map;
		
		ArrayList<ExprNodeDesc> cols = []
		
		exprs.each { e ->
			map = WindowingTypeCheckProcFactory.genExprNode(e, typeChkCtx)
			cols << map.get(e)
		}
		
		
		ArrayList<String> aliases = ["c1", "c2", "c3", "c4", "c5"]
		SelectDesc selectDesc = new SelectDesc(cols, aliases, false)
		SelectOp select = new SelectOp()
		select.initialize(selectDesc, inoI)
		
		
		while( wIn.next(w) != -1)
		{
			Object r = deS.deserialize(w)
			select.process(r)
			println select.output
		}
	}
	
	@Test
	void testCompareExprs()
	{
		ArrayList<ASTNode> exprs = [
			build("p_name"),
			build("lpad(concat(substr(p_name, 1,5), '--'),10, ' ') like '%e%')"),
			build("p_retailprice"),
			build("p_retailprice > 1300"),
			build("not p_retailprice > 1300"),
			build("p_retailprice between 1300 and 1800")
		]
		
		println exprs[5].toStringTree();
		
		HashMap<Node, Object> map;
		
		ArrayList<ExprNodeDesc> cols = []
		
		exprs.each { e ->
			map = WindowingTypeCheckProcFactory.genExprNode(e, typeChkCtx)
			cols << map.get(e)
		}
		
		
		ArrayList<String> aliases = []
		
		0..<exprs.size().each { i ->
			aliases << "c${i}".toString()
		}
		
		SelectDesc selectDesc = new SelectDesc(cols, aliases, false)
		SelectOp select = new SelectOp()
		select.initialize(selectDesc, inoI)
		
		
		while( wIn.next(w) != -1)
		{
			Object r = deS.deserialize(w)
			select.process(r)
			println select.output
		}
	}
	
	public static ASTNode build(String expr) throws WindowingException
	{
		Windowing2Lexer lexer;
		CommonTokenStream tokens;
		Windowing2Parser parser;
		CommonTree t;
		CommonTreeNodeStream nodes;
		QSpecBuilder2 qSpecBldr;
		String err;
		
		try
		{
			lexer = new Windowing2Lexer(new ANTLRStringStream(expr));
			tokens = new CommonTokenStream(lexer);
			parser = new Windowing2Parser(tokens);
			parser.setTreeAdaptor(ParserTest2.adaptor);
			t = parser.expression().getTree()
			
			err = parser.getWindowingParseErrors()
			if ( err != null )
			{
				throw new WindowingException(err)
			}
		}
		catch(Throwable te)
		{
			err = parser.getWindowingParseErrors()
			if ( err != null )
			{
				throw new WindowingException(err)
			}
			throw new WindowingException("Parse Error:" + te.toString(), te)
		}
		
		try
		{
			
			nodes = new CommonTreeNodeStream(t);
			nodes.setTokenStream(tokens)
			qSpecBldr = new QSpecBuilder2(nodes);
			ASTNode node = qSpecBldr.expression()
	
			err = qSpecBldr.getWindowingParseErrors()
			if ( err != null )
			{
				throw new WindowingException(err)
			}
			
			return node
		}
		catch(Throwable te)
		{
			err = qSpecBldr.getWindowingParseErrors()
			if ( err != null )
			{
				throw new WindowingException(err)
			}
			throw new WindowingException("Parse Error:" + te.toString(), te)
		}
	}
}
