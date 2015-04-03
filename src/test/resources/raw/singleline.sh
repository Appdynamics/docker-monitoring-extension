#!/usr/bin/env bash

echo HTTP/1.0 200 OK
echo Content-Type: application/json
echo Job-Name: info
echo Date: Fri, 03 Apr 2015 13:17:06 GMT
echo Content-Length: 764
echo ""
echo '{"Containers":4,"Debug":0,"DockerRootDir":"/var/lib/docker","Driver":"aufs","DriverStatus":[["Root Dir","/var/lib/docker/aufs"],["Backing Filesystem","extfs"],["Dirs","34"]],"ExecutionDriver":"native-0.2","ID":"MDEZ:C7AS:4WMV:T63P:QJM3:GYPE:ZGVE:MJLR:RSJB:OT7V:H6PB:3S7N","IPv4Forwarding":1,"Images":26,"IndexServerAddress":"https://index.docker.io/v1/","InitPath":"/usr/bin/docker","InitSha1":"","KernelVersion":"3.16.0-30-generic","Labels":null,"MemTotal":4145242112,"MemoryLimit":1,"NCPU":1,"NEventsListener":0,"NFd":36,"NGoroutines":48,"Name":"ubuntu","OperatingSystem":"Ubuntu 14.04.2 LTS","RegistryConfig":{"IndexConfigs":{"docker.io":{"Mirrors":null,"Name":"docker.io","Official":true,"Secure":true}},"InsecureRegistryCIDRs":["127.0.0.0/8"]},"SwapLimit":0}'
echo {Hello:world:}