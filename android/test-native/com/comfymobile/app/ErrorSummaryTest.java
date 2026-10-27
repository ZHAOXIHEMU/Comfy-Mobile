package com.comfymobile.app;
import org.json.*;
public final class ErrorSummaryTest {
 public static void main(String[] args)throws Exception{String raw=new JSONArray().put(new JSONArray().put("execution_start").put(new JSONObject())).put(new JSONArray().put("execution_error").put(new JSONObject().put("node_id","12").put("node_type","CLIPTextEncode").put("exception_type","MemoryError").put("exception_message","VBAR allocation failed").put("traceback",new JSONArray().put("FULL PRIVATE PATH STACK")))).toString();String result=BatchEngine.errorSummary(raw);if(!result.contains("节点 12")||!result.contains("VBAR allocation failed")||result.contains("FULL PRIVATE"))throw new AssertionError(result);if(BatchEngine.errorSummary("x".repeat(2000)).length()>601)throw new AssertionError("unbounded error");System.out.println("PASS: execution errors summarized by node; full traceback excluded from summary; message bounded");}
}
