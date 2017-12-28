/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.amqp.rabbit.stocks.config.client;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.rabbit.stocks.config.AbstractStockAppRabbitConfiguration;
import org.springframework.amqp.rabbit.stocks.gateway.RabbitStockServiceGateway;
import org.springframework.amqp.rabbit.stocks.gateway.StockServiceGateway;
import org.springframework.amqp.rabbit.stocks.handler.ClientHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures RabbitTemplate and creates the Trader queue and binding for the
 * client. 与服务端共用一个RabbitTemplate父类，但是配置略有不同
 * 
 * @author Mark Pollack
 * @author Mark Fisher
 */
@Configuration
public class RabbitClientConfiguration extends
		AbstractStockAppRabbitConfiguration {

	@Value("${stocks.quote.pattern}")
	// app.stock.quotes.nasdaq.* 接收消息的pattern
	private String marketDataRoutingKey;

	@Autowired
	private ClientHandler clientHandler;

	/**
	 * The client's template will by default send to the exchange defined in
	 * {@link org.springframework.amqp.rabbit.config.AbstractRabbitConfiguration#rabbitTemplate()}
	 * with the routing key
	 * {@link AbstractStockAppRabbitConfiguration#STOCK_REQUEST_QUEUE_NAME}
	 * <p>
	 * The default exchange will delivery to a queue whose name matches the
	 * routing key value.
	 * Exchange为default，即无名的Exchange，RoutingKey为app.stock.request，这是客户端发送信息的配置
	 * 也就是说客户端的信息将发送至匿名的Exchange，RoutingKey为app.stock.request的通道
	 */
	@Override
	public void configureRabbitTemplate(RabbitTemplate rabbitTemplate) {
		rabbitTemplate.setRoutingKey(STOCK_REQUEST_QUEUE_NAME);// 客户端将信息发送到defaultExchange，RouteKey为app.stock.request
	}

	/**
	 * 这个bean主要用于从客户端向服务端发送交易请求
	 * 
	 * @return
	 */
	@Bean
	public StockServiceGateway stockServiceGateway() {
		RabbitStockServiceGateway gateway = new RabbitStockServiceGateway();
		gateway.setRabbitTemplate(rabbitTemplate());
		// 此处设置DefaultReplyTo为traderJoeQueue().getName()，它将作为一个回调的Queue，接收来自服务端的响应。
		// 隐式的注入RabbitTemplate对象到RabbitStockServiceGateway中
		// 这个bean在client-message.xml中也有配置
		gateway.setDefaultReplyTo(traderJoeQueue().getName());
		return gateway;
	}

	/**
	 * 这个bean用于监听服务端发过来的消息，包含两类消息，一类是服务端发送的行情消息，另外一类是 服务端处理完客户端的交易请求以后的响应消息
	 * 
	 * @return
	 */
	@Bean
	public SimpleMessageListenerContainer messageListenerContainer() {
		SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(
				connectionFactory());
		// 设置该监听器监听的为marketDataQueue()和traderJoeQueue()，其中marketDataQueue()绑定了app.stock.marketdata这个Exchange和
		// app.stock.quotes.nasdaq.*这个routeKey。所以他可以监听到服务端发过来的nasdaq交易所下的证券信息
		// traderJoeQueue()是一个系统自动命名的Queue,当客户端发送trade
		// request会用它作为确认（replyTo)的Queue,好让服务端在处理完后发送确认信息到这个Queue
		// 所以此处我们也要监听它。这个bean在client-message.xml中也有配置
		container.setConcurrentConsumers(5);
		container.setQueues(marketDataQueue(), traderJoeQueue());
		container.setMessageListener(messageListenerAdapter());
		container.setAcknowledgeMode(AcknowledgeMode.AUTO);
		return container;

		// container(using(connectionFactory()).listenToQueues(marketDataQueue(),
		// traderJoeQueue()).withListener(messageListenerAdapter()).
	}

	/**
	 * 这个bean为监听适配器，主要的作用是监听消息
	 * 
	 * @return
	 */
	@Bean
	public MessageListenerAdapter messageListenerAdapter() {
		//
		return new MessageListenerAdapter(clientHandler, jsonMessageConverter());
	}

	// Broker Configuration

	// @PostContruct
	// public void declareClientBrokerConfiguration() {
	// declare(marketDataQueue);
	// declare(new Binding(marketDataQueue, MARKET_DATA_EXCHANGE,
	// marketDataRoutingKey));
	// declare(traderJoeQueue);
	// // no need to bind traderJoeQueue as it is automatically bound to the
	// default direct exchanage, which is what we will use
	//
	// //add as many declare statements as needed like a script.
	// }
	/**
	 * 这个bean是用于接收股票行情的Queue，这是一个匿名的Queue。
	 * 
	 * @return
	 */
	@Bean
	public Queue marketDataQueue() {
		return new AnonymousQueue();
	}

	/**
	 * Binds to the market data exchange. Interested in any stock quotes.
	 */
	/**
	 * 将marketDataQueue与发送股票行情的topic Exchange关联，并且以marketDataRoutingKey作为绑定
	 * 的key。这样就可以接收特定的股票行情。
	 * @return
	 */
	@Bean
	public Binding marketDataBinding() {
		return BindingBuilder.bind(marketDataQueue()).to(marketDataExchange())
				.with(marketDataRoutingKey);
	}

	/**
	 * This queue does not need a binding, since it relies on the default
	 * exchange.
	 * 该bean用于接收服务端发送回来的响应消息。
	 */
	@Bean
	public Queue traderJoeQueue() {
		return new AnonymousQueue();
	}

	@Bean
	public AmqpAdmin rabbitAdmin() {
		return new RabbitAdmin(connectionFactory());
	}

}
