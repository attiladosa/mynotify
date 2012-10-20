package demos;
import java.io.IOException;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.QueueingConsumer;


public class ReceiveLogs {

	private static final String EXCHANGE_NAME = "logs";

	public static void main(String[] argv) throws java.io.IOException,
			java.lang.InterruptedException {

		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(Constants.host);
		Connection connection = factory.newConnection();
		Channel channel = connection.createChannel();

		channel.exchangeDeclare(EXCHANGE_NAME, "fanout");
		channel.exchangeDeclare(EXCHANGE_NAME + "s", "fanout");

		String queueName = channel.queueDeclare().getQueue();

		channel.queueBind(queueName, EXCHANGE_NAME, "");
		channel.queueBind(queueName, EXCHANGE_NAME + "s", "");

		System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

		QueueingConsumer consumer = new QueueingConsumer(channel);
		channel.basicConsume(queueName, true, consumer);

		while (true) {
			QueueingConsumer.Delivery delivery = consumer.nextDelivery();
			String message = new String(delivery.getBody());

			System.out.println(" [x] Received '" + message + "'");
		}
	}
}