#!/usr/bin/env bash

#Do not add any echo statements or other commands that might output any data
#Using NC: This will not work in docker 1.8.2 onwards
echo -e "GET $1 HTTP/1.0\r\n" | sudo nc -U /var/run/docker.sock

# Variant for Debian
#echo -e "GET $1 HTTP/1.0\r\n" | sudo nc -U -q 5 /var/run/docker.sock

#For centos/redhat use a ubuntu image

#Using Socat
#sudo echo "GET $1 HTTP/1.0\r\n" | socat unix-connect:/var/run/docker.sock STDIO

# Using curl - Needs curl version >= 7.40.0
#sudo curl --unix-socket /var/run/docker.sock  http:$1