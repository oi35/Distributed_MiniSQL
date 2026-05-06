cmake_minimum_required(VERSION 3.22)
project(minisql_cpp_client CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(Protobuf CONFIG REQUIRED)
find_package(gRPC CONFIG REQUIRED)

# --- Generate protobuf + gRPC sources at configure/build time -----------

set(PROTO_FILE "${CMAKE_CURRENT_SOURCE_DIR}/proto/gateway.proto")
set(GENERATED_DIR "${CMAKE_CURRENT_BINARY_DIR}/generated")
file(MAKE_DIRECTORY "${GENERATED_DIR}")

set(PROTO_SRCS "${GENERATED_DIR}/gateway.pb.cc")
set(PROTO_HDRS "${GENERATED_DIR}/gateway.pb.h")
set(GRPC_SRCS "${GENERATED_DIR}/gateway.grpc.pb.cc")
set(GRPC_HDRS "${GENERATED_DIR}/gateway.grpc.pb.h")

get_target_property(PROTOC_EXE protobuf::protoc LOCATION)
get_target_property(GRPC_CPP_PLUGIN gRPC::grpc_cpp_plugin LOCATION)

add_custom_command(
    OUTPUT "${PROTO_SRCS}" "${PROTO_HDRS}" "${GRPC_SRCS}" "${GRPC_HDRS}"
    COMMAND "${PROTOC_EXE}"
            --cpp_out "${GENERATED_DIR}"
            --grpc_out "${GENERATED_DIR}"
            "--plugin=protoc-gen-grpc=${GRPC_CPP_PLUGIN}"
            -I "${CMAKE_CURRENT_SOURCE_DIR}/proto"
            "${PROTO_FILE}"
    DEPENDS "${PROTO_FILE}"
    COMMENT "Generating gateway.proto C++/gRPC stubs"
    VERBATIM
)

# --- Static library: minisql_client -------------------------------------

add_library(minisql_client STATIC
    src/minisql_client.cpp
    "${PROTO_SRCS}"
    "${GRPC_SRCS}"
)

target_include_directories(minisql_client
    PUBLIC
        "${CMAKE_CURRENT_SOURCE_DIR}/src"
        "${GENERATED_DIR}"
)

target_link_libraries(minisql_client
    PUBLIC
        gRPC::grpc++
        protobuf::libprotobuf
)

# --- Example binary: minisql_demo ---------------------------------------

add_executable(minisql_demo examples/demo.cpp)
target_link_libraries(minisql_demo PRIVATE minisql_client)
