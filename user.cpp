#include <algorithm>
#include <atomic>
#include <boost/algorithm/string/classification.hpp>
#include <boost/algorithm/string/trim.hpp>
#include <boost/archive/text_iarchive.hpp>
#include <boost/archive/text_oarchive.hpp>
#include <boost/asio.hpp>
#include <boost/bind.hpp>
#include <boost/date_time/posix_time/posix_time.hpp>
#include <boost/enable_shared_from_this.hpp>
#include <boost/make_shared.hpp>
#include <boost/program_options.hpp>
#include <boost/serialization/list.hpp>
#include <boost/serialization/vector.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/thread/thread.hpp>
#include <boost/container/flat_set.hpp>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <deque>
#include <forward_list>
#include <fstream>
#include <iostream>
#include <iterator>
#include <list>
#include <openssl/sha.h>
#include <set>
#include <stack>
#include <vector>
#include "ed25519/ed25519.h"
#include "user.hpp"
#include "settings.hpp"

/*int cmpkey2(std::string& line,uint8_t* key)
{ if(line.length()<4+64){
    return(-1);}
  uint8_t msgtxt[64];
  ed25519_text2key((uint8_t*)msgtxt,line.c_str()+4,32); // do not send last hash
  return(memcmp(msgtxt,key,32));
}*/

// expected format:  :to_bank,to_user,to_mass:to_bank,to_user,to_mass:to_bank,to_user,to_mass ... ;
// the list must terminate with ';'
bool parse_mpt(std::string& text,uint32_t& to_bank,std::string& line)
{ int pos=3;
  int add=0;
  int end=line.length();
  for(;pos<end;pos+=add){
    char to_acct[2+4+8];
    uint32_t tbank;
    uint32_t tuser;
     int64_t tmass;
    if(line[pos]==';'){
      return(true);}
    if(3!=sscanf(line.c_str()+pos,":%X,%X,%lX%n",&tbank,&tuser,&tmass,&add) || !add){
      return(false);}
    memcpy(to_acct+0,&tbank,2);
    memcpy(to_acct+2,&tuser,4);
    memcpy(to_acct+6,&tmass,8);
    text.append(to_acct,2+4+8);
    to_bank++;}
  return(false);
}

usertxs_ptr run(settings& sts,std::string& line)
{ uint32_t to_bank=0; //actually uint16_t
  uint32_t to_user=0;
   int64_t to_mass=0;
  uint64_t to_info=0;
  uint32_t now=time(NULL);
  if(!strncmp(line.c_str(),"ME::",4)){ // get info about me
    usertxs_ptr txs(new usertxs(TXSTYPE_INF,sts.bank,sts.user,sts.bank,sts.user,now));
    txs->sign(sts.ha,sts.sk,sts.pk);
    return(txs);}
  else if(sscanf(line.c_str(),"INF:%X:%X",&to_bank,&to_user)){ // get info about a different user
    usertxs_ptr txs(new usertxs(TXSTYPE_INF,sts.bank,sts.user,to_bank,to_user,now));
    txs->sign(sts.ha,sts.sk,sts.pk);
    return(txs);}
  else if(sscanf(line.c_str(),"LOG:%X",&now)){ // get info about me and my log since 'now'
    usertxs_ptr txs(new usertxs(TXSTYPE_LOG,sts.bank,sts.user,now)); //now==0xffffffff => fix log file if needed
    txs->sign(sts.ha,sts.sk,sts.pk);
    return(txs);}
  else if(!strncmp(line.c_str(),"BRO:",4)){ // broadcast message
    usertxs_ptr txs(new usertxs(TXSTYPE_BRO,sts.bank,sts.user,sts.msid,now,line.length()-4,to_user,to_mass,to_info,line.c_str()+4));
    txs->sign(sts.ha,sts.sk,sts.pk);
    return(txs);}
  else if(!strncmp(line.c_str(),"MPT:",4)){ // send funds to multiple to-accounts
    std::string text;
    if(!parse_mpt(text,to_bank,line) || !to_bank){
      return(NULL);}
    usertxs_ptr txs(new usertxs(TXSTYPE_MPT,sts.bank,sts.user,sts.msid,now,to_bank,to_user,to_mass,to_info,text.c_str()));
    txs->sign(sts.ha,sts.sk,sts.pk);
    return(txs);}
  else if(sscanf(line.c_str(),"PUT:%X:%X:%lX:%lX",&to_bank,&to_user,&to_mass,&to_info)){ // send funds
    usertxs_ptr txs(new usertxs(TXSTYPE_PUT,sts.bank,sts.user,sts.msid,now,to_bank,to_user,to_mass,to_info,NULL));
    txs->sign(sts.ha,sts.sk,sts.pk);
    return(txs);}
  else if(sscanf(line.c_str(),"USR:%X",&to_bank)){ // create new user @ to_bank
    usertxs_ptr txs(new usertxs(TXSTYPE_USR,sts.bank,sts.user,sts.msid,now,to_bank,to_user,to_mass,to_info,NULL));
    txs->sign(sts.ha,sts.sk,sts.pk);
    return(txs);}
  else if(!strncmp(line.c_str(),"BNK:",4)){ // create new bank
    usertxs_ptr txs(new usertxs(TXSTYPE_BNK,sts.bank,sts.user,sts.msid,now,to_bank,to_user,to_mass,to_info,NULL));
    txs->sign(sts.ha,sts.sk,sts.pk);
    return(txs);}
  else if(sscanf(line.c_str(),"GET:%X:%X",&to_bank,&to_user)){ // retreive funds
    usertxs_ptr txs(new usertxs(TXSTYPE_GET,sts.bank,sts.user,sts.msid,now,to_bank,to_user,to_mass,to_info,NULL));
    txs->sign(sts.ha,sts.sk,sts.pk);
    return(txs);}
  else if(!strncmp(line.c_str(),"KEY:",4)){ // change user key
    //if(!check_key2(line)){
    //if(cmpkey2(line,sts.pn)){
    //  return(NULL);}
    if(line.length()<4+2*32+1*2*64){
      fprintf(stderr,"ERROR, bad KEY format; should be:\nKEY:new_public_key:empty_string_signature\n");
      return(NULL);}
    hash_t key;
    ed25519_signature sig;
    ed25519_text2key(key,line.c_str()+4,32); // do not send last hash
    ed25519_text2key(sig,line.c_str()+4+2*32+1,64); // do not send last hash
    usertxs_ptr txs(new usertxs(TXSTYPE_KEY,sts.bank,sts.user,sts.msid,now,to_bank,to_user,to_mass,to_info,(const char*)key));
    txs->sign(sts.ha,sts.sk,sts.pk);
    if(txs->sign2(sig)){
      fprintf(stderr,"ERROR, bad new KEY empty string signature\n");
      return(NULL);}
    return(txs);}
  else if(!strncmp(line.c_str(),"BKY:",4)){ // change bank key
    //if(cmpkey2(line,sts.pn)){
    //  return(NULL);}
    hash_t key;
    ed25519_text2key(key,line.c_str()+4,32); // do not send last hash
    usertxs_ptr txs(new usertxs(TXSTYPE_BKY,sts.bank,sts.user,sts.msid,now,to_bank,to_user,to_mass,to_info,(const char*)key));
    txs->sign(sts.ha,sts.sk,sts.pk);
    //txs->sign2(sts.ha,sts.sn,sts.pn); // server shoudl sign this !!!
    //txs->sign3(sts.ha,sts.so,sts.po); // now needed, messages signed by the server wthi old key anyway 
    return(txs);}
  else if(sscanf(line.c_str(),"BLG:%X",&now)){ // get last broadcast log from block before now
    usertxs_ptr txs(new usertxs(TXSTYPE_BLG,sts.bank,sts.user,now)); //now==0xffffffff => fix log file if needed
    txs->sign(sts.ha,sts.sk,sts.pk);
    return(txs);}
  else{
    return(NULL);}
}

void print_user(user_t& u)
{  char pkey[64];
   char hash[64];
   ed25519_key2text(pkey,u.pkey,32);
   ed25519_key2text(hash,u.hash,32);
   fprintf(stdout,
     "msid:%08X time:%08X stat:%04X node:%04X user:%08X lpath:%08X rpath:%08X balance:%016lX\npkey:%.64s\nhash:%.64s\n",
     u.msid,u.time,u.stat,u.node,u.user,u.lpath,u.rpath,u.weight,pkey,hash);
}

void print_log(log_t* log,int len)
{ int fd=open("usr.log",O_RDONLY|O_CREAT,0640);
  write(fd,(char*)log,len);
  close(fd);
  if(len%sizeof(log_t)){
    std::cerr<<"ERROR, bad log allignment, viewer not yet implemanted, hex-view usr.log to investigate\n";}
  for(log_t* end=log+len/sizeof(log_t);log<end;log++){
    fprintf(stdout,"%08X ?%04X b%04X u%08X m%08X x%08X y%08X v%016lX\n",
      log->time,log->type,log->node,log->user,log->umid,log->nmid,log->mpos,log->weight);}
}

void save_blg(char* blg,int len,uint32_t path) // blg is 4 bytes longer than len (includes path in 4 bytes)
{ char filename[64];
  sprintf(filename,"bro.%08X",path);
  int fd=open(filename,O_WRONLY|O_CREAT|O_TRUNC,0644);
  if(len){
    write(fd,(char*)blg,len);}
  close(fd);
}

void talk(boost::asio::ip::tcp::socket& socket,settings& sts,usertxs_ptr txs) //len can be deduced from txstype
{ char buf[0xff];
  try{
    txs->print_head();
    txs->print();
    boost::asio::write(socket,boost::asio::buffer(txs->data,txs->size));
    if(txs->ttype==TXSTYPE_BLG){
      uint32_t head[2];
      if(2*sizeof(uint32_t)!=boost::asio::read(socket,boost::asio::buffer(head,2*sizeof(uint32_t)))){
        std::cerr<<"ERROR reading broadcast log length\n";
        socket.close();
        return;}
      uint32_t path=head[0];
      int len=(int)head[1];
      char* blg=NULL;
      if(len){
        blg=(char*)malloc(len);//last 4 bytes: the block time of the broadcast log file
        if(blg==NULL){
          fprintf(stderr,"ERROR allocating %08X bytes\n",len);
          socket.close();
          return;}
        if(len!=(int)boost::asio::read(socket,boost::asio::buffer(blg,len))){ // exception will ...
          std::cerr<<"ERROR reading broadcast log\n";
          free(blg);
          socket.close();
          return;}}
      save_blg(blg,len,path);
      free(blg);}
    else{
      int len=boost::asio::read(socket,boost::asio::buffer(buf,sizeof(user_t)));
      if(len!=sizeof(user_t)){
        std::cerr<<"ERROR reading confirmation\n";}
      else{
        user_t myuser;
        memcpy(&myuser,buf,sizeof(user_t));
        print_user(myuser);
        if(txs->ttype!=TXSTYPE_INF || ((int)txs->buser==sts.user && (int)txs->bbank==sts.bank)){
          if(txs->ttype!=TXSTYPE_INF && txs->ttype!=TXSTYPE_LOG && (uint32_t)sts.msid+1!=myuser.msid){
            std::cerr<<"ERROR transaction failed (bad msid)\n";}
          if(memcmp(sts.pk,myuser.pkey,32)){
            if(txs->ttype==TXSTYPE_KEY){
              std::cerr<<"PKEY changed\n";}
            else{
              std::cerr<<"PKEY differs\n";}}
          sts.msid=myuser.msid;
          memcpy(sts.ha,myuser.hash,SHA256_DIGEST_LENGTH);}}
      if(txs->ttype==TXSTYPE_INF){
        len=boost::asio::read(socket,boost::asio::buffer(buf,sizeof(user_t)));
        if(len!=sizeof(user_t)){
          std::cerr<<"ERROR reading info\n";}
        else{
          user_t myuser;
          memcpy(&myuser,buf,sizeof(user_t));
          print_user(myuser);}}
      if(txs->ttype==TXSTYPE_LOG){
        int len;
        if(sizeof(int)!=boost::asio::read(socket,boost::asio::buffer(&len,sizeof(int)))){
          std::cerr<<"ERROR reading log length\n";
          socket.close();
          return;}
        log_t* log=(log_t*)std::malloc(len+sizeof(log_t));
        if(len!=(int)boost::asio::read(socket,boost::asio::buffer((char*)log,len))){ // exception will cause leak
          std::cerr<<"ERROR reading log\n";
          free(log);
          socket.close();
          return;}
        print_log(log,len);
        free(log);}}}
  catch (std::exception& e){
    std::cerr << "Exception: " << e.what() << "\n";}
  socket.close();
}

int main(int argc, char* argv[])
{ settings sts;
  sts.get(argc,argv);
  boost::asio::io_service io_service;
  boost::asio::ip::tcp::resolver resolver(io_service);
  boost::asio::ip::tcp::resolver::query query(sts.addr,std::to_string(sts.port).c_str());
  boost::asio::ip::tcp::resolver::iterator endpoint_iterator = resolver.resolve(query);
  boost::asio::ip::tcp::resolver::iterator end;
  boost::asio::ip::tcp::socket socket(io_service);
  boost::system::error_code error = boost::asio::error::host_not_found;
  while (endpoint_iterator != end){
    std::cerr<<"CONNECTING\n";
    //TODO add timeout
    //http://www.boost.org/doc/libs/1_52_0/doc/html/boost_asio/example/timeouts/blocking_tcp_client.cpp
    socket.connect(*endpoint_iterator, error);
    if(!error){
      break;}
    socket.close();
    endpoint_iterator++;}
  if(error){
    throw boost::system::system_error(error);}
  std::cerr<<"CONNECTED\n";
  try{
    if(!sts.exec.empty()){
      usertxs_ptr txs=run(sts,sts.exec);
      if(txs==NULL){
        std::cerr << "ERROR\n";}
      talk(socket,sts,txs);
      return(0);}
    std::string line;
    while (std::getline(std::cin,line)){
      if(line[0]=='\n'){
        break;}
      if(line[0]=='.'){
        break;}
      usertxs_ptr txs=run(sts,line);
      if(txs==NULL){
        break;}
      //TODO, send to server
      talk(socket,sts,txs);
      socket.connect(*endpoint_iterator, error);
      if(error){
        std::cerr<<"ERROR connecting again\n";
        break;}}
    socket.close();}
  catch (std::exception& e){
    std::cerr << "Exception: " << e.what() << "\n";}
  return 0;
}
