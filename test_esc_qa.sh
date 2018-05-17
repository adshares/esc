#!/bin/bash
##############################################################################################
# Starts esc-qa tests on esc docker with custom genesis file.
##############################################################################################


##############################################################################################
# Functions
##############################################################################################
# Prints usage
function console_help {
    echo
    echo Usage: [-g genesis] -d dockerization_repo
    echo     -d dockerization_repo : path to dockerization repo
    echo     -g genesis : genesis.json file with network configuration
    echo
}

# Shows progress of sleep function
# base on: https://stackoverflow.com/a/12500894
function sleep_with_progress {
    echo "Wait for $1 sec"

    count=0
    total=$1
    pstr="[=======================================================================]"

    while [ $count -lt $total ]; do
      sleep 0.5
      count=$(( $count + 1 ))
      pd=$(( $count * 73 / $total ))
      printf "\r%3d.%1d%% %.${pd}s" $(( $count * 100 / $total )) $(( ($count * 1000 / $total) % 10 )) $pstr
    done
}

##############################################################################################
# Initialization
##############################################################################################
# delay between docker up and test start in seconds
DELAY_DOCKER_UP=180
WORK_DIR=$( pwd )

##############################################################################################
# Read arguments
##############################################################################################
while [ $# -gt 0 ]
do
    case "$1" in
        -d) PATH_DOCKERIZATION_REPO=$2
            ;;
        -g) FILE_GENESIS=$2
            ;;
        -h|--help|help)
            echo "[help]"
            console_help
            exit 0
            ;;
        -*) echo "invalid option $1"
            ;;
    esac
    shift
done

if [[ -z $PATH_DOCKERIZATION_REPO ]]; then
    echo "missing dockerization path"
    console_help
    exit 1
fi

##############################################################################################
# Copy genesis file to docker
##############################################################################################
if [ -f "$FILE_GENESIS" ]; then
    echo "Copy genesis file to docker"
    cp $FILE_GENESIS ${PATH_DOCKERIZATION_REPO}/development/esc/docker/genesis.json
else
    echo "Skip copying genesis file"
fi

##############################################################################################
# Start esc docker
##############################################################################################
cd ${PATH_DOCKERIZATION_REPO}/development/
./console.sh up esc
sleep_with_progress $DELAY_DOCKER_UP
cd $WORK_DIR

##############################################################################################
# Start tests
##############################################################################################
cmd="mvn test -f pom-esc-qa.xml"
if [ -f "$FILE_GENESIS" ]; then
    cmd="$cmd -Dgenesis.file=$FILE_GENESIS"
fi
echo "Start test with command:"
echo "$cmd"
$cmd
test_result="$?"

##############################################################################################
# Stop esc docker
##############################################################################################
cd ${PATH_DOCKERIZATION_REPO}/development/
./console.sh down esc
cd $WORK_DIR


echo "$test_result"
echo
if [[ "$test_result" -ne 0 ]]; then
    echo "Test finished with error"
else
    echo "Finish successfully"
fi
exit "$test_result"
