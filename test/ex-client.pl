#!/usr/bin/perl

use strict;
use warnings;
use bigint;
use Data::Dumper;
use DBI;
use POSIX qw(strftime);
use IO::Prompt;
use Digest::SHA qw(sha256_hex);
use JSON qw(decode_json);

my $STOCKID=6; # id esc in stocks table (stock_id in users_escs)
my $STOCKNAME='ESC'; # id esc in stocks table (stock_id in users_escs)
my $MAIN="0001-00000001-8B4E";
my $PASSCSUM="73d77ae330af4be0f22513161aa5d342e067629fa4e27e9149a530f61e6a89ea"; # sha256sum($PASS)
my $ESCCSUM="e736b919bb689696efd731eab8824a06fe537b22cab07fd66ecb81724be173e8"; # sha256sum ./esc
my $MINM=0.1; # minimum balance to keep in wallet

my $DEBUG=1;
my $DIR=$ENV{'HOME'};
my $MYDIR="$DIR/.esc";
my $MYLOG="$DIR/.pesc.log";
my $MYBLK="$DIR/.pesc.blk";
my $WWW='exchange.com';
my $MYDB='exchange';
my $MYCNF="$DIR/.my.cnf";

my $SLEEP=5; # sleep 
my $BLOCKSEC=32; # expected block period
my $LASTLOG=0; # time of last log read
my $LOGDEL=0; # reload last 5 seconds from the logs

my $PID=$$;
my $USER=(getpwuid($<))[0];
my $PASS=''; #$ENV{'PASS'};
my $HASH=''; # last hash
my $MSID=0; # last msid
my $MASS=0; # account balance

if(!@ARGV){
  die "USAGE: $0 stop|start|restart|esc|getlogs|confirm_deposit|send_withdrawals [arguments ...]\n";}

chdir($MYDIR)||die"ERROR: failed to change dir to local repository\n";
my $dbh=DBI->connect("dbi:mysql:database=$MYDB;mysql_read_default_file=$MYCNF;mysql_auto_reconnect=1");
if(!$dbh){
  die "ERROR: failed to connect to mysql using $MYCNF";}

if($ARGV[0] eq 'esc'){
  &setpass();
  &run_esc($ARGV[1]);}
elsif($ARGV[0] eq 'getlogs'){
  &setpass();
  &getlogs();}
elsif($ARGV[0] eq 'getblocks'){
  &setpass();
  &getblocks();}
elsif($ARGV[0] eq 'confirm_deposit'){
  &setpass();
  &confirm_deposit();}
elsif($ARGV[0] eq 'send_withdrawals'){
  &setpass();
  &getme();
  &send_withdrawals();}
elsif($ARGV[0] eq 'stop' || $ARGV[0] eq 'restart'){
  &stop();}
elsif($ARGV[0] eq 'start'){
  &start();}
else{
  die "USAGE: $0 stop|start|restart|esc|getlogs|confirm_deposit|send_withdrawals [arguments ...]\n";}

exit(0);

sub setpass
{ if($PASS ne''){
    return;}
  open(FILE,"settings.cfg");
  my @settings=<FILE>;
  close(FILE);
  if(grep(/^secret=/,@settings)){
    return;}
  print "MAIN: $MAIN\n";
  if(prompt(-p=>'Passphrase:',-te=>'*')){
    $PASS=$_;}
  my $check=sha256_hex($PASS);
  if($check!~/$PASSCSUM/){
    die "BAD PASSWORD :-(\n$check\n";}
}

sub stop
{ if(&pid()){
    chmod 0640,"$DIR/.pesc.pid"; # chmod -x to signal: stop
    for(my $i=0;&pid()&& $i<30;$i++)    { sleep 1;}
    if(&pid())  { die "ERROR: stop failed\n"; }
    print STDERR "stopped\n";}
  if($ARGV[0] eq'restart'){
    &start();}
}

sub start
{ my $p=&pid();
  if($p){ 
    print STDERR "ERROR: deamon running ($p)\n";
    exit;}
  &setpass();
  $dbh->disconnect();
  defined($p=fork)||die "ERROR, fork: $!";
  if($p){
    $SIG{CHLD}='IGNORE'; # no future system calls ... can ignore CHLD
    print STDERR "started [$p]\n";
    exit; }
  $PID=$$;
  `echo $PID > $DIR/.pesc.pid`;
  chmod 0750,"$DIR/.pesc.pid";
  open(STDERR,'>>',$MYLOG)||die "ERROR: could not open $MYLOG for writing ($!)";
  &mylog('',"start daemon");

  $dbh=DBI->connect("dbi:mysql:database=$MYDB;mysql_read_default_file=$MYCNF;mysql_auto_reconnect=1");
  if(!$dbh){
    die "ERROR: failed to connect to mysql using $MYCNF";}

  for(;&pid($PID);){
    #$dbh->selectrow_array(qq|select now()|);
    &getblocks();
    &getlogs();
    &confirm_deposit(); #FIX blocknumber tests
    &send_withdrawals();
    sleep($SLEEP);}
  &mylog('',"stop daemon");
}

sub run_esc
{ my $text=shift||return;
  #FIXME, consider opening the pipe only once otherwise replacing ./esc will give access to $PASS
  my $check=`sha256sum ./esc`;
  if($check!~/^$ESCCSUM/){
    die "ERROR: ./esc checksum failed\n";}
  if($HASH eq''){
    open(FILE,qq{|./esc -A $MAIN >esc.out 2>esc.err})||die"ERROR, running ./esc -A $MAIN";}
  else{
    open(FILE,qq{|./esc -A $MAIN -i $MSID -x $HASH >esc.out 2>esc.err})||die"ERROR, running ./esc -A $MAIN -i $MSID -x $HASH";}
  print FILE "$PASS\n$text\n";
  close(FILE);
  open(FILE,'esc.out');
  my @file=<FILE>;
  close(FILE);
  my $res;
  eval{ $res=decode_json(join('',@file)); };
  #print Dumper($res);

  if(defined($res->{'account'})){
    $MASS=$res->{'account'}->{'balance'};
    if($HASH eq'' || ($MSID+1)==$res->{'account'}->{'msid'}){
      $HASH=$res->{'account'}->{'hash'};
      $MSID=$res->{'account'}->{'msid'};}
    elsif($HASH ne $res->{'account'}->{'hash'}){
      die "ERROR: hash mismatch:\n$HASH\n$res->{'account'}->{'hash'}\n".Dumper($res);}}

  return $res;
}

sub getme
{ &run_esc(qq|{"run":"get_me"}|);
}

sub getblocks
{ &run_esc(qq|{"run":"get_blocks"}|);
}

sub getlogs
{ my $result;

  if($ARGV[0] eq 'getlogs' && defined($ARGV[1])){
    $ARGV[1]+=0;
    if(defined($ARGV[2])){
      $ARGV[2]+=0;
      $result=&run_esc(qq|{"run":"get_log","from":$ARGV[1],"to":$ARGV[2]}|);}
    else{
      $result=&run_esc(qq|{"run":"get_log","from":$ARGV[1]}|);}
    print Dumper($result);}
  else{
    if(!$LASTLOG){
      ($LASTLOG)=$dbh->selectrow_array(qq|select unix_timestamp(sync) from users_escs where stock_id=? order by sync desc limit 1|,undef,$STOCKID);}
    if(!$LASTLOG){
      $LASTLOG=$LOGDEL;}
    my $from=$LASTLOG-$LOGDEL;
    $result=&run_esc(qq|{"run":"get_log","from":$from}|);}

  if(!defined($result->{'log'})){
    return;}

  foreach(@{$result->{'log'}}){
    my $res=$_;
    if(defined($res->{'time'})){
      $LASTLOG=$res->{'time'};}
    if($res->{'type'} ne 'send_one'){
      next;}
    if($res->{'inout'} eq 'in'){
      if($res->{'message'}!~/^0{56}[0-9a-fA-F]{8}$/ || $res->{'amount'}<0){
        print STDERR "ERROR, wrong message format\n".Dumper($res);
        next;}
      my $user_id=hex($res->{'message'});
      if(!$user_id){
        next;}
      print STDERR sprintf("user: %d\ntransactionID %s\nfrom: %s\namount: %s\nmessage %s\n",
        $user_id,$res->{'id'},$res->{'address'},$res->{'amount'},$res->{'message'});
      if(!&accept_user($user_id)){
        $dbh->do(qq|insert ignore users_escs (stock_id,user_id,in_out,amount,address,txid,memo,status) values (?,?,'in',?,?,?,0x$res->{'message'},'error')|,undef,$STOCKID,0,$res->{'amount'},$res->{'address'},$res->{'id'});
        print STDERR sprintf("ERROR: user not accepted %s\n",$user_id);}
      else{
        $dbh->do(qq|insert ignore users_escs (stock_id,user_id,in_out,amount,address,txid,memo) values (?,?,'in',?,?,?,0x$res->{'message'})|,undef,$STOCKID,$user_id,$res->{'amount'},$res->{'address'},$res->{'id'});}}
    else{
      #TODO, could check if transaction is ok, but of course there are many other options to withdraw funds
      }}
}

sub confirm_deposit
{ my $sth=$dbh->prepare(qq|select id,user_id,amount,txid,hex(memo) as message from users_escs where status='init' and in_out='in' and sync < now() - interval $BLOCKSEC second |)||die "ERROR: mysql error";
  $sth->execute()||die "ERROR: mysql error";
  while(my $tx=$sth->fetchrow_hashref()){
    my $result=&run_esc(qq|{"run":"get_transaction","txid":"$tx->{'txid'}"}|);
    if(defined($result->{'network_tx'}->{'block_id'})){
      my $es=$result->{'network_tx'};
      if($tx->{'txid'} ne $es->{'id'}){
        $dbh->do(qq|update users_escs set status='error' where id=? and status='init'|,undef,$tx->{'id'});
        print STDERR sprintf("ERROR: txid mismatch %s<>%s\n",$tx->{'txid'},$es->{'id'});
        next;}
      if($tx->{'message'} ne $es->{'message'}){
        $dbh->do(qq|update users_escs set status='error' where id=? and status='init'|,undef,$tx->{'id'});
        print STDERR sprintf("ERROR: memo mismatch %s<>%s\n",$tx->{'message'},$es->{'message'});
        next;}
      if($tx->{'user_id'} != hex($es->{'message'})){
        $dbh->do(qq|update users_escs set status='error' where id=? and status='init'|,undef,$tx->{'id'});
        print STDERR sprintf("ERROR: user_id mismatch %s<>%s\n",$tx->{'user_id'},hex($es->{'message'}));
        next;}
      if($tx->{'amount'} != $es->{'amount'}){
        $dbh->do(qq|update users_escs set status='error' where id=? and status='init'|,undef,$tx->{'id'});
        print STDERR sprintf("ERROR: amount mismatch %s<>%s\n",$tx->{'amount'},$es->{'amount'});
        next;}
      if(!&accept_user($tx->{'user_id'})){
        $dbh->do(qq|update users_escs set status='error' where id=? and status='init'|,undef,$tx->{'id'});
        print STDERR sprintf("ERROR: bad user_id %s\n",$tx->{'user_id'});
        next;}
      $dbh->do(qq|update users_escs set status='busy',block=? where id=? and status='init'|,undef,$es->{'block_id'},$tx->{'id'})||die "ERROR: update users_escs ".$tx->{'id'}."\n";
      $dbh->do(qq|insert users_stocks (user_id,stock_id,owned) select id,$STOCKID,cast($tx->{'amount'} as decimal(38,18)) from users where id=? on duplicate key update owned=owned+$tx->{'amount'}|,undef,$tx->{'user_id'})||die "ERROR: insert users_escs ".$tx->{'id'}."\n";
      $dbh->do(qq|update stocks set traded=traded+$tx->{'amount'}, shares=if(shares<traded+$tx->{'amount'},traded+$tx->{'amount'},shares) where id=$STOCKID|);
      $dbh->do(qq|update users_escs set status='done' where id=? and status='busy'|,undef,$tx->{'id'})||die "ERROR: update users_escs ".$tx->{'id'}."\n";
      print STDERR "DONE: confirmed $tx->{'user_id'},$tx->{'amount'},$es->{'block_id'},$tx->{'txid'}\n";}}
}

sub accept_user
{ my $id=shift||return(0);
  (my $user_ok)=$dbh->selectrow_array(qq|select id from users where id=?|,undef,$id);
  if(!defined($user_ok)){
    return(0);}
  #FIXME, do housekeeping !!!
  return(1);
}

sub send_withdrawals
{ my $sth=$dbh->prepare(qq|select id,user_id,-amount as `amount`,address from users_escs where status='queued' and in_out='out' and stock_id=$STOCKID|)||die "ERROR: mysql error";
  $sth->execute()||die "ERROR: mysql error";
  while(my $tx=$sth->fetchrow_hashref()){
    if(!&accept_user($tx->{'user_id'})){
      $dbh->do(qq|update users_escs set status='error' where id=? and status='queued'|,undef,$tx->{'id'});
      print STDERR sprintf("ERROR accepting user %d\n",$tx->{'user_id'});
      next;}
    print STDERR sprintf("ACCEPTING accepting user %d, withdrawal %d\n",$tx->{'user_id'},$tx->{'id'});
    # send email
    (my $email)=$dbh->selectrow_array(qq|select email from users where id=?|,undef,$tx->{'user_id'});
    my $task="WITHDRAW:$STOCKID:".$tx->{'id'}.":".$tx->{'user_id'}.":".$tx->{'address'};
    my $hash=sha256_hex($PASS.$task);
    $dbh->do(qq|insert hashes (user_id,task) values (?,?)|,undef,$tx->{'user_id'},$task)||die "ERROR: insert hashes (user_id,task) values ($tx->{'user_id'},$task)";
    my $hash_id=$dbh->{'mysql_insertid'};
    open(FILE,"|/usr/sbin/sendmail -t")||die;
    print FILE <<END;
To: $email
From: session\@$WWW
Subject: $STOCKNAME withdrawal initiated

Dear Exchange User

You have initiated a withdrawal of $tx->{'amount'} $STOCKNAME to the address:
$tx->{'address'} .
Please follow this link to confirm the withdrawal:

https://$WWW/users/confirm/$hash_id/$hash

Sincerely Yours,

The Exchange Session Manager
END
    close FILE;
    $dbh->do(qq|update users_escs set status='accepted' where id=? and status='queued'|,undef,$tx->{'id'});}

  # read confirmations
  $sth=$dbh->prepare(qq|select * from hashes where hash is not null|)||die "ERROR: mysql error";
  $sth->execute()||die "ERROR: mysql error";
  while(my $tx=$sth->fetchrow_hashref()){
    if($tx->{'task'}=~/^WITHDRAW:$STOCKID:(\d+):(\d+):(....-........-....)$/){
      my $id=$1;
      my $user_id=$2;
      my $address=$3;
      my $hash=sha256_hex($PASS.$tx->{'task'});
      if($hash eq $tx->{'hash'}){
        $dbh->do(qq|update users_escs set status='confirmed' where id=? and user_id=? and stock_id=$STOCKID and address=? and status='accepted' and in_out='out'|,undef,$id,$user_id,$address)||die"ERROR: update users_escs $id,$user_id,$address\n";
        $dbh->do(qq|delete from hashes where id=?|,undef,$tx->{'id'});
        print STDERR "task $tx->{'task'} confirmed\n";}
      else{
        #$dbh->do(qq|call cancel_stocks($STOCKID,?,?)|,undef,$id,$user_id);
        #$dbh->do(qq|delete from hashes where id=?|,undef,$tx->{'id'});
        $dbh->do(qq|update hashes set hash=null where id=?|,undef,$tx->{'id'});
        print STDERR "ERROR: task $tx->{'task'} failed to confirm\n";}}}

  # queue
  $sth=$dbh->prepare(qq|select id,user_id,-amount as `amount`,address,hex(memo) as message from users_escs where status='confirmed' and in_out='out' and block=0|)||die "ERROR: mysql error";
  $sth->execute()||die "ERROR: mysql error";
  while(my $tx=$sth->fetchrow_hashref()){
    if(!&accept_user($tx->{'user_id'})){
      $dbh->do(qq|update users_escs set status='error' where id=? and status='confirmed'|,undef,$tx->{'id'});
      print STDERR sprintf("ERROR accepting user %d\n",$tx->{'user_id'});
      next;}
    if($MASS-$MINM<$tx->{'amount'}){
        print STDERR sprintf("BALANCE too low for $STOCKNAME withdrawal %f<%f\n",$MASS-$MINM,$tx->{'amount'});
        next;}
    print STDERR sprintf("START withdrawal %d (%s to %s)\n",$tx->{'id'},$tx->{'amount'},$tx->{'address'});
    $dbh->do(qq|update users_escs set status='busy' where id=? and status='confirmed'|,undef,$tx->{'id'});
    my $result=&run_esc(qq|{"run":"send_one","address":"$tx->{'address'}","amount":$tx->{'amount'},"message":"$tx->{'message'}"}|);
    if(defined($result->{'tx'}->{'id'})){
      $dbh->do(qq|update users_escs set status='done',txid=?,block=0 where id=?|,undef,$result->{'tx'}->{'id'},$tx->{'id'});
      print STDERR sprintf("BUSY withdrawal %d (%s to %s)\n",$tx->{'id'},$tx->{'amount'},$tx->{'address'});}
    else{
      print STDERR sprintf("ERROR withdrawal %d (%s to %s)\n",$tx->{'id'},$tx->{'amount'},$tx->{'address'});}}

  # fill block number
  $sth=$dbh->prepare(qq|select id,user_id,-amount as `amount`,txid,address from users_escs where status='done' and in_out='out' and block=0 and sync < now() - interval $BLOCKSEC second|)||die "ERROR: mysql error";
  $sth->execute()||die "ERROR: mysql error";
  while(my $tx=$sth->fetchrow_hashref()){
    my $result=&run_esc(qq|{"run":"get_transaction","txid":"$tx->{'txid'}"}|);
    if(defined($result->{'network_tx'}->{'block_id'})){
      print STDERR sprintf("DONE withdrawal %d (%s to %s)\n",$tx->{'id'},$tx->{'amount'},$tx->{'address'});
      $dbh->do(qq|update users_escs set block=? where id=? and block=0|,undef,$result->{'network_tx'}->{'block_id'},$tx->{'id'});}}
}

sub pid
{ my $PID=shift||0;
  my $ps;
  if(-r "$DIR/.pesc.pid"){
    my $pid=`cat $DIR/.pesc.pid`+0;
    if($PID){
      if($PID != $pid){
        return 0;}
      if(!-x "$DIR/.pesc.pid"){
        unlink("$DIR/.pesc.pid");
        return 0;}
      system("touch $DIR/.pesc.pid");
      return $PID;}
    elsif($pid){
      $ps=`ps h -o pid -o user:10 -o comm -p $pid`;
      if($ps!~/$pid *$USER.*ex-client/){
        unlink("$DIR/.pesc.pid");
        return 0;}}
    return $pid;}
  return 0;
}

sub mylog
{ my $ptr=shift;
  my $message=shift||die "ERROR: internal error";
  my $data='';

  if($ptr eq''){
    $data="-";}
  elsif(exists $ptr->{'user_account'}){ # $users
    $data=join(":",$ptr->{'id'}||'',$ptr->{'name'}||'',$ptr->{'user_account'}||'');}
  elsif(exists $ptr->{'ipo_account'}){ # $stocks
    $data=join(":",$ptr->{'id'}||'',$ptr->{'code'}||'',$ptr->{'ipo_account'}||'');}
  elsif(exists $ptr->{'transactionHash'} || exists $ptr->{'details'}){ # users_bitcoins or stocks_bitcoins
    $data=join(":",$ptr->{'id'}||'',$ptr->{'user_id'}||$ptr->{'stock_id'}||'',$ptr->{'amount'}||'',$ptr->{'fee'}||'',$ptr->{'transactionHash'}||'');}
  else{
    use Data::Dumper;
    print STDERR Dumper($ptr);
    die "ERROR: internal error";}

  open(STDERR,'>>',$MYLOG)||die "ERROR: could not open $MYLOG for writing ($!)";
  my $time=strftime("%Y-%m-%d %H:%M:%S",localtime());
  print STDERR "$time\t$data\t$message\n";
}

