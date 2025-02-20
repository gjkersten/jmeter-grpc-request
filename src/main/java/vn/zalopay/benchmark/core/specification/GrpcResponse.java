package vn.zalopay.benchmark.core.specification;

import com.google.protobuf.Any;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.protobuf.StatusProto;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class GrpcResponse {

    private boolean success;
    private Throwable throwable;
    private final List<Object> output;

    public GrpcResponse() {
        output = new ArrayList<>();
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void setThrowable(Throwable throwable, Descriptors.MethodDescriptor descriptor) {
        this.throwable = throwable;
        if (throwable instanceof StatusRuntimeException) {
            StatusRuntimeException statusRuntimeException = (StatusRuntimeException) throwable;
            findMeldingTypes(descriptor)
                .map(this::descriptorToDefaultInstance)
                .map(meldingInstance -> findMeldingInTrailers(statusRuntimeException, meldingInstance))
                .forEach(output::add);
        }
    }

    private DynamicMessage descriptorToDefaultInstance(Descriptor descriptor) {
        return DynamicMessage.getDefaultInstance(descriptor);
    }

    private Stream<Descriptor> findMeldingTypes(MethodDescriptor descriptor) {
        return Stream.concat(
            descriptor.getFile().getMessageTypes().stream(),
            descriptor.getFile().getDependencies().stream().flatMap(dep -> dep.getMessageTypes().stream()))
            .filter(mt -> mt.getName().endsWith("Melding"));
    }

    private DynamicMessage findMeldingInTrailers(StatusRuntimeException sre, DynamicMessage meldingInstance)  {
        com.google.rpc.Status status = StatusProto.fromStatusAndTrailers(sre.getStatus(), sre.getTrailers());
        if (status.getDetailsCount() > 0) {
            // Details added using io.grpc.protobuf.StatusProto
            return Optional.of(status)
                .map(com.google.rpc.Status::getDetailsList)
                .flatMap(det -> det.stream().findFirst())
                .map(any -> anyToDynamicMessage(meldingInstance, any))
                .orElseThrow(() -> sre);
        } else {
            // Details added using metadata directly on io.grpc.Status
            return sre.getTrailers().get(ProtoUtils.keyForProto(meldingInstance));
        }
    }

    private DynamicMessage anyToDynamicMessage(DynamicMessage defaultInstance, Any any) {
        try {
            DynamicMessage result = defaultInstance.getParserForType().parseFrom(any.getValue());
            if (result == null) {
                throw new IllegalArgumentException("Parsing `Any` to a protobuf message resulted in null");
            }
            return result;
        }
        catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Parsing `Any` to a protobuf message failed", e);
        }
    }

    public void storeGrpcMessage(Object message) {
        output.add(message);
    }

    public String getGrpcMessageString() {
        if (output.size() == 1) {
            return output.get(0).toString();
        }

        return output.toString();
    }
}
