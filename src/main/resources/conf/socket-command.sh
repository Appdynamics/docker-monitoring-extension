#!/usr/bin/env bash

#Do not add any echo statements or other commands that might output any data
#Using NC
echo -e "GET $1 HTTP/1.0\r\n" | sudo nc -U /var/run/docker.sock

#Using Socat
#sudo echo "GET $1 HTTP/1.0\r\n" | socat unix-connect:/var/run/docker.sock STDIO