package com.damon.utils;


import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class EnvUtil {

    public static StreamExecutionEnvironment getEnv() {
        //1、获取执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        //1.1 设置CK&状态后端
//        env.setStateBackend(new FsStateBackend("hdfs://localhost:8020/gmall-flink/ck"));
//        env.enableCheckpointing(5000L);
//        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
//        env.getCheckpointConfig().setCheckpointTimeout(10000L);
//        env.getCheckpointConfig().setMaxConcurrentCheckpoints(2);
//        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(3000);

        return env;
    }
}
