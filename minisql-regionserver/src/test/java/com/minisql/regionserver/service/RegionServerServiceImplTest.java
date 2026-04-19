package com.minisql.regionserver.service;

import com.minisql.regionserver.proto.*;
import com.minisql.common.proto.ErrorCode;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * RegionServerServiceImpl 单元测试
 */
public class RegionServerServiceImplTest {

    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private RegionServerServiceImpl service;

    @Before
    public void setUp() {
        service = new RegionServerServiceImpl("rs-test");
    }

    @Test
    public void testPutAndGet() {
        // 准备测试数据
        PutRequest putRequest = PutRequest.newBuilder()
                .setTableName("users")
                .setRegionId("region-001")
                .putColumns("name", com.google.protobuf.ByteString.copyFromUtf8("Alice"))
                .putColumns("age", com.google.protobuf.ByteString.copyFromUtf8("30"))
                .build();

        // 执行PUT操作
        TestStreamObserver<PutResponse> putObserver = new TestStreamObserver<>();
        service.put(putRequest, putObserver);

        // 验证PUT响应
        assertTrue(putObserver.hasResponse());
        PutResponse putResponse = putObserver.getResponse();
        assertTrue(putResponse.getSuccess());
        assertEquals(ErrorCode.SUCCESS, putResponse.getErrorCode());

        // 执行GET操作
        GetRequest getRequest = GetRequest.newBuilder()
                .setTableName("users")
                .setRegionId("region-001")
                .setKey(com.google.protobuf.ByteString.copyFromUtf8("key1"))
                .build();

        TestStreamObserver<GetResponse> getObserver = new TestStreamObserver<>();
        service.get(getRequest, getObserver);

        // 验证GET响应
        assertTrue(getObserver.hasResponse());
        GetResponse getResponse = getObserver.getResponse();
        assertTrue(getResponse.getFound());
        assertEquals(ErrorCode.SUCCESS, getResponse.getErrorCode());
        assertEquals("Alice", getResponse.getColumnsMap().get("name").toStringUtf8());
        assertEquals("30", getResponse.getColumnsMap().get("age").toStringUtf8());
    }

    @Test
    public void testDelete() {
        // 先插入数据
        PutRequest putRequest = PutRequest.newBuilder()
                .setTableName("users")
                .setRegionId("region-001")
                .putColumns("name", com.google.protobuf.ByteString.copyFromUtf8("Bob"))
                .build();

        TestStreamObserver<PutResponse> putObserver = new TestStreamObserver<>();
        service.put(putRequest, putObserver);
        assertTrue(putObserver.getResponse().getSuccess());

        // 执行DELETE操作
        DeleteRequest deleteRequest = DeleteRequest.newBuilder()
                .setTableName("users")
                .setRegionId("region-001")
                .setKey(com.google.protobuf.ByteString.copyFromUtf8("key1"))
                .build();

        TestStreamObserver<DeleteResponse> deleteObserver = new TestStreamObserver<>();
        service.delete(deleteRequest, deleteObserver);

        // 验证DELETE响应
        assertTrue(deleteObserver.hasResponse());
        DeleteResponse deleteResponse = deleteObserver.getResponse();
        assertTrue(deleteResponse.getSuccess());
        assertTrue(deleteResponse.getExisted());
        assertEquals(ErrorCode.SUCCESS, deleteResponse.getErrorCode());

        // 验证数据已被删除
        GetRequest getRequest = GetRequest.newBuilder()
                .setTableName("users")
                .setRegionId("region-001")
                .setKey(com.google.protobuf.ByteString.copyFromUtf8("key1"))
                .build();

        TestStreamObserver<GetResponse> getObserver = new TestStreamObserver<>();
        service.get(getRequest, getObserver);

        GetResponse getResponse = getObserver.getResponse();
        assertFalse(getResponse.getFound());
    }

    @Test
    public void testExists() {
        // 测试不存在的key
        ExistsRequest existsRequest = ExistsRequest.newBuilder()
                .setTableName("users")
                .setRegionId("region-001")
                .setKey(com.google.protobuf.ByteString.copyFromUtf8("nonexistent"))
                .build();

        TestStreamObserver<ExistsResponse> existsObserver = new TestStreamObserver<>();
        service.exists(existsRequest, existsObserver);

        assertTrue(existsObserver.hasResponse());
        ExistsResponse existsResponse = existsObserver.getResponse();
        assertFalse(existsResponse.getExists());

        // 插入数据后测试
        PutRequest putRequest = PutRequest.newBuilder()
                .setTableName("users")
                .setRegionId("region-001")
                .putColumns("name", com.google.protobuf.ByteString.copyFromUtf8("Charlie"))
                .build();

        TestStreamObserver<PutResponse> putObserver = new TestStreamObserver<>();
        service.put(putRequest, putObserver);
        assertTrue(putObserver.getResponse().getSuccess());

        // 再次测试exists
        existsObserver = new TestStreamObserver<>();
        service.exists(existsRequest, existsObserver);

        existsResponse = existsObserver.getResponse();
        assertTrue(existsResponse.getExists());
    }

    /**
     * 简单的StreamObserver实现，用于测试
     */
    private static class TestStreamObserver<T> implements io.grpc.stub.StreamObserver<T> {
        private T response;
        private Throwable error;
        private boolean completed = false;

        @Override
        public void onNext(T value) {
            this.response = value;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
            this.completed = true;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }

        public boolean hasResponse() {
            return response != null;
        }

        public T getResponse() {
            return response;
        }

        public Throwable getError() {
            return error;
        }

        public boolean isCompleted() {
            return completed;
        }
    }
}