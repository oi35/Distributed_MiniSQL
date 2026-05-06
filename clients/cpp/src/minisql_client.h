// MiniSQL C++ client SDK.
// Thin wrapper around the gRPC GatewayService exposed by the Java GatewayServer.

#pragma once

#include <memory>
#include <optional>
#include <string>
#include <unordered_map>
#include <variant>
#include <vector>
#include <stdexcept>

namespace minisql {

// Matches the TypedValue oneof from gateway.proto.
using Value = std::variant<
    std::monostate,  // null
    int64_t,
    double,
    std::string,
    bool
>;

struct QueryResult {
    std::vector<std::string> columns;
    // One map per row: column name -> value. Same order as `columns`.
    std::vector<std::unordered_map<std::string, Value>> rows;
};

struct UpdateResult {
    int32_t affected_rows = 0;
};

using ExecuteResult = std::variant<UpdateResult, QueryResult>;

class MiniSQLError : public std::runtime_error {
public:
    MiniSQLError(std::string message, std::string error_code)
        : std::runtime_error(message),
          error_code_(std::move(error_code)) {}

    const std::string& error_code() const noexcept { return error_code_; }

private:
    std::string error_code_;
};

// Blocking client. Not thread-safe; create one per thread or serialize calls.
class MiniSQLClient {
public:
    // `target` is a gRPC address like "localhost:9090".
    explicit MiniSQLClient(const std::string& target);
    ~MiniSQLClient();

    MiniSQLClient(const MiniSQLClient&) = delete;
    MiniSQLClient& operator=(const MiniSQLClient&) = delete;

    struct PingResponse {
        std::string version;
        int64_t server_time_ms = 0;
    };

    PingResponse ping();

    ExecuteResult execute(const std::string& sql);

    // Convenience: throws MiniSQLError if the statement did not return a query.
    QueryResult fetchall(const std::string& sql);

    // Convenience: returns affected rows. Throws if result is a query.
    int32_t execute_update(const std::string& sql);

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

}  // namespace minisql
