#!/bin/bash

function parseInput() {
  cmd_line="$0"
  plugins=$DEFAULT_PLUGIN
  while [ $# -gt 0 ]; do
    case $1 in
      --java_home)
        java_home="$2"
        cmd_line=$cmd_line" --java_home "$java_home
		echo $cmd_line
        shift
        shift
        ;;
      --plugins)
        plugins="$2"
        cmd_line=$cmd_line" --plugins "$plugins
        shift
        shift
        ;;
      *)
        other_args=${other_args}" "${1}
        cmd_line=$cmd_line" "$other_args
        shift
        ;;
    esac
  done
  echo $cmd_line
  plugin_array=(${plugins//,/ })
}

function initEnvironment() {
  if [ -z "${USER_LANGUAGE}" ]
  then
    USER_LANGUAGE=${DEFAULT_LANGUAGE}
  fi
  if [ -z "${USER_COUNTRY}" ]
  then
    USER_COUNTRY=${DEFAULT_COUNTRY}
  fi
}

function startProcess() {
  SVC_CP=${WORK_DIR}/classes:${WORK_DIR}/lib/*
  for plugin in ${plugin_array[@]}
  do
    if [ -n ${plugin} ]; then
       SVC_CP=$SVC_CP:/opt/inc/plugins/${plugin}/lib/*
    fi
  done
  echo "classpath is: $SVC_CP"

  if [[ ! -n ${java_home} ]]
  then
    echo "java_home HAS NOT BEEN SET. GOING TO USE JAVA AT JAVA_HOME: "
    echo $(java -version)
    echo Executing: java -cp "${SVC_CP}" $JAVA_OPTS ${MAIN_CLASS} ${other_args}
    nohup java -cp "${SVC_CP}" $JAVA_OPTS ${MAIN_CLASS} ${other_args}> /dev/null 2>&1 &
  else
    echo "java_home HAS BEEN SET TO: "$java_home
    echo Executing: ${java_home}/bin/java -cp "${SVC_CP}" $JAVA_OPTS ${MAIN_CLASS} ${other_args}
    nohup ${java_home}/bin/java -cp "${SVC_CP}" $JAVA_OPTS ${MAIN_CLASS} ${other_args} > /dev/null 2>&1 &
  fi

  if [ 0 -eq $? ]
  then
   echo $! | tee "${svc_pid}"
  fi
}

pushd . >/dev/null
cd `dirname $0`
cd ..
WORK_DIR=`pwd`
svc_pid=${WORK_DIR}/proc.pid
java_home=""
plugin_array=()
other_args=""
source ${WORK_DIR}/bin/variables.sh
pid=$(ps -aux | grep ${MAIN_CLASS} | grep -v grep | awk '{print $2}')

parseInput $@
initEnvironment

JAVA_OPTS="-Duser.language=${USER_LANGUAGE} -Duser.country=${USER_COUNTRY}"
JAVA_OPTS="${JAVA_OPTS} -Xms512m -Xmx2048m"
JAVA_OPTS="${JAVA_OPTS} -XX:MetaspaceSize=80m -XX:MaxMetaspaceSize=128m"
JAVA_OPTS="${JAVA_OPTS} -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=utf-8"

if [ -n "${pid}" ]
then
  echo process has exsited already.
else
  startProcess
fi
popd > /dev/null