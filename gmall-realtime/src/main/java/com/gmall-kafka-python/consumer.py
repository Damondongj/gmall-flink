from pykafka import KafkaClient

# 连接Kafka
client = KafkaClient(hosts="0.0.0.0:9092")

# 定义消费者
# 选择 topic 为 test 的消费
topic = client.topics["ods_base_log"]
consumer = topic.get_simple_consumer(
    consumer_group="ods_base_log",
    auto_commit_enable=True,
    auto_commit_interval_ms=1,
    consumer_id='mock'
)

# 消费
for message in consumer:
    if message is not None:
        kafka_str = message.value.decode()
        print(kafka_str)