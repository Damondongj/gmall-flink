package com.damon.app.dwm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.damon.utils.MyKafkaUtil;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.PatternTimeoutFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.OutputTag;

import java.time.Duration;

import static com.damon.utils.EnvUtil.getEnv;

public class UserJumpDetailApp {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = getEnv();

        String sourceTopic = "dwd_page_id";
        String groupId = "userJumpDetailApp";
        String sinkTopic = "dwm_user_jump_detail";
        DataStreamSource<String> kafkaDS = env.addSource(MyKafkaUtil.getKafkaConsumer(sourceTopic, groupId));

        SingleOutputStreamOperator<JSONObject> jsonObjDS = kafkaDS.map(JSON::parseObject)
                .assignTimestampsAndWatermarks(WatermarkStrategy
                        .<JSONObject>forBoundedOutOfOrderness(Duration.ofSeconds(1))
                        .withTimestampAssigner((SerializableTimestampAssigner<JSONObject>) (element, recordTimestamp) -> element.getLong("ts")));

        // 定义模式序列
        Pattern<JSONObject, JSONObject> pattern = Pattern.<JSONObject>begin("start").where(new SimpleCondition<JSONObject>() {
            @Override
            public boolean filter(JSONObject value) {
                String lastPageId = value.getJSONObject("page").getString("last_page_id");
                return lastPageId == null || lastPageId.length() <= 0;
            }
        }).next("next").where(new SimpleCondition<JSONObject>() {
            @Override
            public boolean filter(JSONObject value) {
                String lastPageId = value.getJSONObject("page").getString("last_page_id");
                return lastPageId == null || lastPageId.length() <= 0;
            }
        }).within(Time.seconds(10));

        // 使用循环模式  定义模式序列
        Pattern.<JSONObject>begin("start").where(new SimpleCondition<JSONObject>() {
                    @Override
                    public boolean filter(JSONObject value) {
                        String lastPageId = value.getJSONObject("page").getString("last_page_id");
                        return lastPageId == null || lastPageId.length() <= 0;
                    }
                })
                .times(2)
                .consecutive()  //指定严格临近(next)
                .within(Time.seconds(10));

        // 将模式序列作用到流上
        PatternStream<JSONObject> patternStream = CEP
                .pattern(jsonObjDS.keyBy(json -> json.getJSONObject("common").getString("mid"))
                        , pattern);

        // 提取匹配上的和超时事件
        OutputTag<JSONObject> timeOutTag = new OutputTag<JSONObject>("timeOut") {
        };
        SingleOutputStreamOperator<JSONObject> selectDS = patternStream.select(timeOutTag,
                (PatternTimeoutFunction<JSONObject, JSONObject>) (map, l) -> map.get("start").get(0), (PatternSelectFunction<JSONObject, JSONObject>) map -> map.get("start").get(0));

        DataStream<JSONObject> timeOutDS = selectDS.getSideOutput(timeOutTag);

        // union 两种事件
        DataStream<JSONObject> unionDS = selectDS.union(timeOutDS);

        // 将数据写入kafka
        unionDS.print();
        unionDS.map(JSONArray::toJSONString)
                .addSink(MyKafkaUtil.getKafkaProducer(sinkTopic));

        env.execute("UserJumpDetailApp");
    }
}
