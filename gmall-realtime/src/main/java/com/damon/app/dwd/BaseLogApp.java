package com.damon.app.dwd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.damon.utils.MyKafkaUtil;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.text.SimpleDateFormat;

import static com.damon.utils.EnvUtil.getEnv;

public class BaseLogApp {
    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = getEnv();

        //2、消费ods_base_log 主题数据创建流
        String sourceTopic = "ods_base_log";
        String groupId = "base_log_app";
        // 从kafka获取数据
        DataStreamSource<String> kafkaDS = env.addSource(MyKafkaUtil.getKafkaConsumer(sourceTopic, groupId));

        //3、将每行数据转换为JSON对象
        // 侧输出流, 脏数据
        OutputTag<String> outputTag = new OutputTag<String>("Dirty") {
        };
        SingleOutputStreamOperator<JSONObject> jsonObjDS = kafkaDS.process(new ProcessFunction<String, JSONObject>() {
            @Override
            public void processElement(String value, ProcessFunction<String, JSONObject>.Context ctx, Collector<JSONObject> out) throws Exception {
                try {
                    // 输出主流
                    JSONObject jsonObject = JSON.parseObject(value);
                    out.collect(jsonObject);
                } catch (Exception e) {
                    //发生异常，json数据无法解析，将数据写入侧输出流
                    ctx.output(outputTag, value);
                }
            }
        });

        //打印脏数据
        jsonObjDS.getSideOutput(outputTag).print("----------Dirty---------");

        //4、新老用户校验 状态编程
        // 根据mid字段来进行分类 keyBy
        // valueState中保存每次数据过来的is_New字段，
        // 保存每个mid的首次访问日期，每条进入该算子的访问记录，都会把每条
        // mid对应的首次访问时间读取出来，只要首次访问时间不为空，则认为该访客是
        // 老访客，否则是新访客
        // 如果是新访客且没有访问记录的话，会写入首次访问时间
        SingleOutputStreamOperator<JSONObject> jsonObjWithNewFlagDS = jsonObjDS
                .keyBy(jsonObj -> jsonObj.getJSONObject("common").getString("mid"))
                .map(new RichMapFunction<JSONObject, JSONObject>() {

                    private ValueState<String> firstVisitState;
//                    private SimpleDateFormat simpleDateFormat;

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        firstVisitState = getRuntimeContext().getState(new ValueStateDescriptor<String>("value-state", String.class));
//                        simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    }

                    @Override
                    public JSONObject map(JSONObject value) throws Exception {

                        //获取数据中的"is_new"标记
                        String isNew = value.getJSONObject("common").getString("is_new");

                        //判断isNew标记是否为"1"  是1
                        if ("1".equals(isNew)) {

                            //获取状态数据
                            String firstDate = firstVisitState.value();
                            Long ts = value.getLong("ts");

                            // valueState里面的值不为null
                            if (firstDate != null) {
                                //修改isNew状态， 将valueState里面的字段改为0
                                value.getJSONObject("common").put("is_new", "0");
                            } else {
                                // valueState里面的值为null
                                // 将valueState中的字段改为1
//                                firstVisitState.update(simpleDateFormat.format(ts));
                                firstVisitState.update("1");
                            }
                        }
                        return value;
                    }
                });

        //5、分流 侧输出流  页面：主流   启动：侧输出流  曝光：侧输出流
        OutputTag<String> startTag = new OutputTag<String>("start") {
        };
        OutputTag<String> displayTag = new OutputTag<String>("display") {
        };
        SingleOutputStreamOperator<String> pageDS = jsonObjWithNewFlagDS.process(new ProcessFunction<JSONObject, String>() {
            @Override
            public void processElement(JSONObject value, ProcessFunction<JSONObject, String>.Context ctx, Collector<String> out) throws Exception {

                // 获取启动日志字段
                String start = value.getString("start");
                if (start != null && start.length() > 0) {
                    // 将数据写入启动日志侧输出流
                    ctx.output(startTag, value.toJSONString());
                } else {
                    // 将数据写入页面日志数主流
                    out.collect(value.toJSONString());

                    // 获取数据中的曝光数据
                    JSONArray displays = value.getJSONArray("displays");

                    if (displays != null && displays.size() > 0) {

                        // 获取页面ID
                        String pageId = value.getJSONObject("page").getString("page_id");

                        for (int i = 0; i < displays.size(); i++) {
                            JSONObject display = displays.getJSONObject(i);

                            display.put("page_id", pageId);

                            //将输出写出道曝光侧输出流
                            ctx.output(displayTag, display.toJSONString());
                        }
                    }
                }
            }
        });

        //6、提取侧输出流
        DataStream<String> startDS = pageDS.getSideOutput(startTag);
        DataStream<String> displayDS = pageDS.getSideOutput(displayTag);

        //7、将三个流进行打印并输出到对应的kafka主题中
        startDS.print("-----------start------------");
        pageDS.print("------------page------------");
        displayDS.print("-------------display-----------");

        startDS.addSink(MyKafkaUtil.getKafkaProducer("dwd_start_log"));
        pageDS.addSink(MyKafkaUtil.getKafkaProducer("dwd_page_log"));
        displayDS.addSink(MyKafkaUtil.getKafkaProducer("dwd_display_log"));

        //8、启动任务
        env.execute("baseLogApp");
    }
}
