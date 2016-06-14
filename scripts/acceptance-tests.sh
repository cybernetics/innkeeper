#!/usr/bin/env bash

if [ "$1" != "-fast" ]; then
  echo "full build"

  sbt assembly
  echo

  docker --version
  docker-compose --version
  echo

  docker-compose build
fi

if [ "$1" == "-nat" ]; then
  echo "init nat"

  VBoxManage controlvm "default" natpf1 "tcp-port6767,tcp,,6767,,6767"
  VBoxManage controlvm "default" natpf1 "tcp-port6768,tcp,,6768,,6768"
  VBoxManage controlvm "default" natpf1 "tcp-port5433,tcp,,5433,,5433"
  VBoxManage controlvm "default" natpf1 "tcp-port9080,tcp,,9080,,9080"
fi

docker-compose up -d
docker-compose ps
sbt it:test
