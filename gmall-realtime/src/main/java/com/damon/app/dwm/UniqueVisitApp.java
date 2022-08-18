package com.damon.app.dwm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONAware;
import com.alibaba.fastjson.JSONObject;
import com.damon.utils.MyKafkaUtil;
import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.text.SimpleDateFormat;

import static com.damon.utils.EnvUtil.getEnv;

public class UniqueVisitApp {
    public static void main(String[] args) throws Exception {
        /*
          统计日活跃量uv
          每天第一次访问的用户的个数
          通过用户的lastPageId为null来进行判断
          如果一个用户第一天晚上23点59分的状态一直保持到第二天开始，那么第二天开始这
          个数据的状态不算

          通过状态编程，现将同一个mid里面的数据分到同一个key里面

          对于每一个分组里面的数据，现将每天第一次来的数据的时间放到状态里面
          lastPageId 为null 放入状态，
          对于下一次来的数据，如果是同一天的数据，直接过滤掉
          如果时间不是同一天的，更新key的状态
         */

        StreamExecutionEnvironment env = getEnv();

        String groupId = "unique_visit_app_210325";
        String sourceTopic = "dwd_page_log";
        String sinkTopic = "dwm_unique_visit";
        DataStreamSource<String> kafkaDS = env.addSource(MyKafkaUtil.getKafkaConsumer(sourceTopic, groupId));

        SingleOutputStreamOperator<JSONObject> jsonObjDS = kafkaDS.map(JSON::parseObject);

        KeyedStream<JSONObject, String> keyedStream = jsonObjDS.keyBy(jsonObj -> jsonObj.getJSONObject("common").getString("mid"));
        SingleOutputStreamOperator<JSONObject> uvDS = keyedStream.filter(new RichFilterFunction<JSONObject>() {

            private ValueState<String> dateState;
            private SimpleDateFormat simpleDateFormat;


            @Override
            public void open(Configuration parameters) {
                ValueStateDescriptor<String> valueStateDescriptor = new ValueStateDescriptor<>("date-state", String.class);
                // 设置状态的超时时间以及更新时间的方式
                StateTtlConfig stateTtlConfig = new StateTtlConfig
                        .Builder(Time.hours(24))
                        .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                        .build();
                valueStateDescriptor.enableTimeToLive(stateTtlConfig);
                dateState = getRuntimeContext().getState(valueStateDescriptor);

                simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            }

            @Override
            public boolean filter(JSONObject value) throws Exception {
                // 取出上一条页面信息
                // 如果这一条数据中没有last_page_id这个字段，那么得到的就是null
                String lastPageId = value.getJSONObject("page").getString("last_page_id");

                // 判断上一条页面是否为Null
                if (lastPageId == null || lastPageId.length() <= 0) {
                    // 取出状态数据
                    String lastDate = dateState.value();
                    // 取出今天的日期
                    String curDate = simpleDateFormat.format(value.getLong("ts"));

                    // 判断两个日期是否相同，两个日期不相同，更新状态
                    if (!curDate.equals(lastDate)) {
                        dateState.update(curDate);
                        return true;
                    }
                }
                return false;
            }
        });

        // 将数据写入状态
        uvDS.print();
        uvDS.map(JSONAware::toJSONString)
                .addSink(MyKafkaUtil.getKafkaProducer(sinkTopic));

        env.execute("UniqueVisitApp");

    }
}
