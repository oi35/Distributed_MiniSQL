// Simple demo for the MiniSQL C++ client.
// Usage:
//   minisql_demo [gateway_address]   (default: localhost:9090)

#include <cstdint>
#include <iostream>
#include <string>

#include "minisql_client.h"

namespace {

std::string Stringify(const minisql::Value& v) {
    struct Visitor {
        std::string operator()(std::monostate) const { return "NULL"; }
        std::string operator()(int64_t x) const { return std::to_string(x); }
        std::string operator()(double x) const { return std::to_string(x); }
        std::string operator()(const std::string& x) const { return x; }
        std::string operator()(bool x) const { return x ? "true" : "false"; }
    };
    return std::visit(Visitor{}, v);
}

void PrintRows(const minisql::QueryResult& result) {
    for (const auto& col : result.columns) {
        std::cout << col << "\t";
    }
    std::cout << "\n";
    for (const auto& row : result.rows) {
        for (const auto& col : result.columns) {
            auto it = row.find(col);
            std::cout << (it == row.end() ? "-" : Stringify(it->second)) << "\t";
        }
        std::cout << "\n";
    }
}

}  // namespace

int main(int argc, char** argv) {
    std::string target = argc > 1 ? argv[1] : "localhost:9090";
    try {
        minisql::MiniSQLClient client(target);
        auto ping = client.ping();
        std::cout << "connected to gateway " << ping.version << "\n";

        std::cout << "INSERT 3 rows\n";
        client.execute_update(
            "INSERT INTO users (user_id, username) VALUES (1, 'alice')");
        client.execute_update(
            "INSERT INTO users (user_id, username) VALUES (2, 'bob')");
        client.execute_update(
            "INSERT INTO users (user_id, username) VALUES (15, 'carol')");

        std::cout << "\nSELECT user_id >= 10:\n";
        PrintRows(client.fetchall("SELECT user_id, username FROM users WHERE user_id >= 10"));

        std::cout << "\nDELETE WHERE user_id = 1 -> "
                  << client.execute_update("DELETE FROM users WHERE user_id = 1")
                  << " row(s)\n";
    } catch (const minisql::MiniSQLError& e) {
        std::cerr << "MiniSQL error [" << e.error_code() << "]: " << e.what() << "\n";
        return 1;
    } catch (const std::exception& e) {
        std::cerr << "unexpected: " << e.what() << "\n";
        return 2;
    }
    return 0;
}
