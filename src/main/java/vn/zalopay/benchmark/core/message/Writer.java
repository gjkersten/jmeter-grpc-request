package vn.zalopay.benchmark.core.message;

import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import io.grpc.stub.StreamObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.zalopay.benchmark.core.specification.GrpcResponse;

public class Writer<T extends Message> implements StreamObserver<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Writer.class);

    private final JsonFormat.Printer jsonPrinter;
    private final GrpcResponse grpcResponse;
    private final Descriptors.MethodDescriptor methodDescriptor;

    Writer(JsonFormat.Printer jsonPrinter, GrpcResponse grpcResponse, Descriptors.MethodDescriptor methodDescriptor) {
        this.jsonPrinter = jsonPrinter.preservingProtoFieldNames().includingDefaultValueFields();
        this.grpcResponse = grpcResponse;
        this.methodDescriptor = methodDescriptor;
    }

    /** Creates a new Writer which writes the messages it sees to the supplied Output. */
    public static <T extends Message> Writer<T> create(
            GrpcResponse grpcResponse, JsonFormat.TypeRegistry registry, Descriptors.MethodDescriptor methodDescriptor) {
        return new Writer<>(JsonFormat.printer().usingTypeRegistry(registry), grpcResponse, methodDescriptor);
    }

    @Override
    public void onCompleted() {
        LOGGER.debug("On completed gRPC message: {}", grpcResponse.getGrpcMessageString());
    }

    @Override
    public void onError(Throwable throwable) {
        grpcResponse.setSuccess(false);
        grpcResponse.setThrowable(throwable, methodDescriptor);
    }

    @Override
    public void onNext(T message) {
        try {
            grpcResponse.setSuccess(true);
            grpcResponse.storeGrpcMessage(jsonPrinter.print(message));
        } catch (InvalidProtocolBufferException e) {
            LOGGER.warn(e.getMessage());
            grpcResponse.storeGrpcMessage(message.toString());
        }
    }
}
