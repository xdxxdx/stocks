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

package org.springframework.amqp.rabbit.stocks.config.server;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.stocks.config.AbstractStockAppRabbitConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures RabbitTemplate for the server.
 * 
 * @author Mark Pollack
 * @author Mark Fisher
 */
@Configuration
public class RabbitServerConfiguration extends
		AbstractStockAppRabbitConfiguration {

	/**
	 * The server's template will by default send to the topic exchange named
	 * {@link AbstractStockAppRabbitConfiguration#MARKET_DATA_EXCHANGE_NAME}.
	 * 服务端继承自AbstractStockAppRabbitConfiguration
	 * ，重写了父类方法configureRabbitTemplate，将topic
	 * Exchange的名称定为MARKET_DATA_EXCHANGE_NAME，这样发送消息的时候就不必每次指定Exchange这个参数
	 */
	public void configureRabbitTemplate(RabbitTemplate rabbitTemplate) {
		rabbitTemplate.setExchange(MARKET_DATA_EXCHANGE_NAME);
	}

	/**
	 * We don't need to define any binding for the stock request queue, since
	 * it's relying on the default (no-name) direct exchange to which every
	 * queue is implicitly bound.
	 * 生成一个绑定了默认（无名称）的DirectExchange的Queue实例，routekey为该queue自己的名称，这个队列是服务为股票交易开设的（股票请求队列）。
	 * 服务端监听这个队列里面的消息(即交易请求，来自客户端),并做处理
	 * 名称为：app.stock.request
	 */
	@Bean
	public Queue stockRequestQueue() {
		return new Queue(STOCK_REQUEST_QUEUE_NAME);
	}

}
