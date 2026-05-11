#include "minisql_client.h"

#include <grpcpp/grpcpp.h>

#include "gateway.grpc.pb.h"

namespace minisql {

namespace pb = ::minisql::gateway;

namespace {

Value UnwrapTypedValue(const pb::TypedValue& value) {
    switch (value.value_case()) {
        case pb::TypedValue::kNullValue:
            return std::monostate{};
        case pb::TypedValue::kIntValue:
            return value.int_value();
        case pb::TypedValue::kDoubleValue:
            return value.double_value();
        case pb::TypedValue::kStringValue:
            return value.string_value();
        case pb::TypedValue::kBoolValue:
            return value.bool_value();
        case pb::TypedValue::VALUE_NOT_SET:
        default:
            return std::monostate{};
    }
}

QueryResult DecodeQueryResult(const pb::QueryResult& proto) {
    QueryResult out;
    out.columns.reserve(proto.columns_size());
    for (const auto& col : proto.columns()) {
        out.columns.push_back(col);
    }
    out.rows.reserve(proto.rows_size());
    for (const auto& proto_row : proto.rows()) {
        std::unordered_map<std::string, Value> row;
        row.reserve(out.columns.size());
        const int n = std::min(proto_row.values_size(),
                               static_cast<int>(out.columns.size()));
        for (int i = 0; i < n; ++i) {
            row.emplace(out.columns[i], UnwrapTypedValue(proto_row.values(i)));
        }
        out.rows.push_back(std::move(row));
    }
    return out;
}

}  // namespace

struct MiniSQLClient::Impl {
    std::shared_ptr<::grpc::Channel> channel;
    std::unique_ptr<pb::GatewayService::Stub> stub;
};

MiniSQLClient::MiniSQLClient(const std::string& target)
    : impl_(std::make_unique<Impl>()) {
    impl_->channel = ::grpc::CreateChannel(target, ::grpc::InsecureChannelCredentials());
    impl_->stub = pb::GatewayService::NewStub(impl_->channel);
}

MiniSQLClient::~MiniSQLClient() = default;

MiniSQLClient::PingResponse MiniSQLClient::ping() {
    pb::PingRequest request;
    pb::PingResponse response;
    ::grpc::ClientContext context;
    ::grpc::Status status = impl_->stub->Ping(&context, request, &response);
    if (!status.ok()) {
        throw MiniSQLError("Ping RPC failed: " + status.error_message(),
                           "RPC_ERROR");
    }
    return PingResponse{response.version(), response.server_time_ms()};
}

ExecuteResult MiniSQLClient::execute(const std::string& sql) {
    pb::ExecuteRequest request;
    request.set_sql(sql);
    pb::ExecuteResponse response;
    ::grpc::ClientContext context;
    ::grpc::Status status = impl_->stub->Execute(&context, request, &response);
    if (!status.ok()) {
        throw MiniSQLError("Execute RPC failed: " + status.error_message(),
                           "RPC_ERROR");
    }
    if (!response.success()) {
        throw MiniSQLError(response.error_message(), response.error_code());
    }
    switch (response.result_case()) {
        case pb::ExecuteResponse::kUpdate: {
            UpdateResult ur;
            ur.affected_rows = response.update().affected_rows();
            return ur;
        }
        case pb::ExecuteResponse::kQuery:
            return DecodeQueryResult(response.query());
        default:
            throw MiniSQLError("gateway returned no result payload", "EMPTY_RESULT");
    }
}

QueryResult MiniSQLClient::fetchall(const std::string& sql) {
    ExecuteResult result = execute(sql);
    if (auto* q = std::get_if<QueryResult>(&result)) {
        return std::move(*q);
    }
    throw MiniSQLError("expected a query result for: " + sql, "WRONG_RESULT_KIND");
}

int32_t MiniSQLClient::execute_update(const std::string& sql) {
    ExecuteResult result = execute(sql);
    if (auto* u = std::get_if<UpdateResult>(&result)) {
        return u->affected_rows;
    }
    throw MiniSQLError("expected an update result for: " + sql, "WRONG_RESULT_KIND");
}

}  // namespace minisql
