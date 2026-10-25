package com.ticket.platform.service;

import com.ticket.platform.config.AsyncOrderProperties;
import com.ticket.platform.entity.GrabRequest;
import com.ticket.platform.mapper.GrabRequestMapper;
import com.ticket.platform.mq.OrderMessageProducer;
import com.ticket.platform.state.GrabRequestStatus;
import com.ticket.platform.state.PublishState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Outbox 发布器单元测试：发布成功/失败后的状态更新。
 */
@ExtendWith(MockitoExtension.class)
class OrderOutboxRelayTest {

    @Mock
    private GrabRequestMapper requestMapper;
    @Mock
    private OrderMessageProducer producer;

    private OrderOutboxRelay relay;

    @BeforeEach
    void setUp() {
        AsyncOrderProperties properties = new AsyncOrderProperties();
        properties.setRelayBatchSize(10);
        properties.setPublishRetryBaseSeconds(2);
        properties.setPublishRetryMaxSeconds(60);
        properties.setRepublishIntervalSeconds(30);
        relay = new OrderOutboxRelay(requestMapper, producer, properties);
    }

    @Test
    void publishFailureMarksRetryWait() {
        GrabRequest request = pendingRequest();
        when(requestMapper.selectList(any())).thenReturn(List.of(request));
        when(requestMapper.claimForPublish(eq(request.getRequestId()), any(), any()))
                .thenReturn(1);
        when(producer.publish(request.getRequestId())).thenReturn(false);

        relay.relayPending();

        verify(requestMapper).markPublishFailed(
                eq(request.getRequestId()), any(), anyString(), any());
        verify(requestMapper, never()).markPublishConfirmed(any(), any(), any());
    }

    @Test
    void publishSuccessMarksConfirmed() {
        GrabRequest request = pendingRequest();
        when(requestMapper.selectList(any())).thenReturn(List.of(request));
        when(requestMapper.claimForPublish(eq(request.getRequestId()), any(), any()))
                .thenReturn(1);
        when(producer.publish(request.getRequestId())).thenReturn(true);

        relay.relayPending();

        verify(requestMapper).markPublishConfirmed(
                eq(request.getRequestId()), any(), any());
        verify(requestMapper, never()).markPublishFailed(any(), any(), anyString(), any());
    }

    private GrabRequest pendingRequest() {
        GrabRequest request = new GrabRequest();
        request.setRequestId("relay-test-request-0001");
        request.setStatus(GrabRequestStatus.PENDING);
        request.setPublishState(PublishState.NEW);
        request.setPublishAttempts(0);
        request.setNextPublishTime(LocalDateTime.now().minusSeconds(1));
        return request;
    }
}
