# MiniSQL C++ Client

Thin C++17 SDK that talks to the Java `GatewayServer`.

## Prerequisites

- **Visual Studio 2022** (or later) with the *Desktop development with C++* workload
- **CMake 3.22+**
- vcpkg (the VS-bundled copy at `<VS install>\VC\vcpkg\vcpkg.exe` works fine)

## First build

The project pins its gRPC + Protobuf dependencies in `vcpkg.json`, so CMake will
trigger vcpkg to download and build them on the first configure. This takes
30–60 minutes on a cold cache and produces several GB under
`<vcpkg>/downloads` + `<vcpkg>/buildtrees`.

```powershell
# From this directory:
cmake -S . -B build -G Ninja `
  -DCMAKE_BUILD_TYPE=Release `
  -DCMAKE_TOOLCHAIN_FILE=D:/VS2022/VC/vcpkg/scripts/buildsystems/vcpkg.cmake `
  -DVCPKG_TARGET_TRIPLET=x64-windows
cmake --build build
```

The path after `-DCMAKE_TOOLCHAIN_FILE` should point at your
`vcpkg/scripts/buildsystems/vcpkg.cmake`. If you installed vcpkg separately,
adjust accordingly.

Or just run the convenience script (it activates the VS Developer environment):

```powershell
.\build_cpp.bat
```

## Usage

```cpp
#include "minisql_client.h"

minisql::MiniSQLClient client("localhost:9090");
client.execute_update("INSERT INTO users (user_id, username) VALUES (1, 'alice')");
auto result = client.fetchall("SELECT user_id, username FROM users WHERE user_id = 1");
```

Run the built-in demo after starting a Java gateway on port 9090:

```powershell
.\build\minisql_demo.exe
```

## Notes

- The generated stubs live in `build/generated/` and are regenerated from
  `proto/gateway.proto` whenever it changes.
- `gateway.proto` is a verbatim copy of
  `minisql-client/src/main/proto/gateway.proto`. If you modify the schema in
  the Java module, copy the file over or set up a symlink.
