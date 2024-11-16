Server IP Addresses Client/Server:
139.62.210.102
139.62.210.103

Login Information:
User: n#
Password: 


wget https://download.oracle.com/java/23/latest/jdk-23_linux-x64_bin.tar.gz
mkdir bin
tar -zxf jdk-23_linux-x64_bin.tar.gz -C ~/bin/
export JAVA_HOME=~/bin/jdk-23.0.1
export PATH="$JAVA_HOME/bin:$PATH"