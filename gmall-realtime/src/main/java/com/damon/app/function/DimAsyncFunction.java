package com.damon.app.function;

import com.alibaba.fastjson.JSONObject;
import com.damon.common.GmallConfig;
import com.damon.utils.DimUtil;
import com.damon.utils.ThreadPoolUtil;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Collections;
import java.util.concurrent.ThreadPoolExecutor;

public abstract class DimAsyncFunction<T> extends RichAsyncFunction<T, T> implements DimAsyncJoinFunction<T> {

    private Connection connection;
    private ThreadPoolExecutor threadPoolExecutor;

    private final String tableName;

    public DimAsyncFunction(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        Class.forName(GmallConfig.PHOENIX_DRIVER);
        connection = DriverManager.getConnection(GmallConfig.PHOENIX_SERVER);

        threadPoolExecutor = ThreadPoolUtil.getThreadPool();
    }

    @Override
    public void asyncInvoke(T input, ResultFuture<T> resultFuture) throws Exception {

        threadPoolExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    //获取查询的主键
                    String id = getKey(input);

                    //查询维度信息
                    JSONObject dimInfo = DimUtil.getDimInfo(connection, tableName, id);

                    //补充维度信息
                    if (dimInfo != null) {
                        join(input, dimInfo);
                    }

                    //将数据输出
                    resultFuture.complete(Collections.singletonList(input));

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

    }


    @Override
    // 当异步 I/O 请求超时时，默认情况下会引发异常并重新启动作业。如果要处理超时，可以覆盖该AsyncFunction#timeout方法。
    public void timeout(T input, ResultFuture<T> resultFuture) throws Exception {
        System.out.println("TimeOut:" + input);
    }
}