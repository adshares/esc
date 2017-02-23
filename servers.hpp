#ifndef SERVERS_HPP
#define SERVERS_HPP

//FIXME, concider separating header and nodes into header.hpp and node.hpp
#pragma pack(1)
/*typedef struct header_s {
	uint32_t now; // start time of block, MUST BE FIRST ELEMENT
	uint32_t txs; // number of transactions in block, FIXME, should be uint16_t
	uint32_t nod; // number of nodes in block, this could be uint16_t later, FIXME, should be uint16_t
	uint8_t oldhash[SHA256_DIGEST_LENGTH]; // previous hash
	uint8_t txshash[SHA256_DIGEST_LENGTH]; // hash of transactions
	uint8_t nodhash[SHA256_DIGEST_LENGTH]; // hash of nodes
	uint8_t nowhash[SHA256_DIGEST_LENGTH]; // current hash
	uint16_t vok; // vip ok votes stored by server, not signed !!! MUST BE LAST
	uint16_t vno; // vip no votes stored by server, not signed !!! MUST BE LAST
} header_t;*/
typedef struct headlink_s { // header links sent when syncing
	uint32_t txs; // number of transactions in block, FIXME, should be uint16_t
	uint32_t nod; // number of nodes in block, this could be uint16_t later, FIXME, should be uint16_t
	uint8_t txshash[SHA256_DIGEST_LENGTH]; // hash of transactions
	uint8_t nodhash[SHA256_DIGEST_LENGTH]; // hash of nodes
} headlink_t;
typedef uint8_t svsi_t[2+(2*SHA256_DIGEST_LENGTH)]; // server_id + signature
typedef struct node_s {
	ed25519_public_key pk; // public key
	uint8_t hash[SHA256_DIGEST_LENGTH]; // hash of accounts
	//uint8_t xash[SHA256_DIGEST_LENGTH]; // hash of additional data
	uint8_t msha[SHA256_DIGEST_LENGTH]; // hash of last message
	uint32_t msid; // last message, server closed if msid==0xffffffff
	uint32_t mtim; // time of last message
	//uint32_t mtim; // block of last message, FIXME, consider adding
	 int64_t weight; // weight in units of 8TonsOfMoon (-1%) (in MoonBlocks)
	uint32_t status; // placeholder for future status settings, can include hash type
	//uint32_t weight; // weight in units of 8TonsOfMoon (-1%) (in MoonBlocks)
	uint32_t users; // placeholder for users (size)
	uint32_t port;
	uint32_t ipv4;
	//char host[64];
} node_t;
#pragma pack()
	
class node
{
public:
	node() :
		pk{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
		hash{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
		msha{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
		msid(0),
		mtim(0),
		weight(0),
		status(0),
		users(0),
		port(0),
		ipv4(0)
		//addr(""),
	{	//bzero(host,64);
	}
	ed25519_public_key pk; // public key
	uint8_t hash[SHA256_DIGEST_LENGTH]; // hash of accounts
	uint8_t msha[SHA256_DIGEST_LENGTH]; // hash of last message
	uint32_t msid; // last message, server closed if msid==0xffffffff
	uint32_t mtim; // time of last message
	 int64_t weight; // placeholder for server weight (total assets)
	uint32_t status; // placeholder for future status settings
	//uint32_t weight; // placeholder for server weight (total assets)
	uint32_t users;
	uint32_t port; // port
	uint32_t ipv4; // ipv4

	//char host[64]; // hostname or address ... remove later
	//std::string addr; // hostname or address ... remove later
	//std::list<int> in; // incomming connections
	//std::list<int> out; // outgoing connections
	std::vector<uint64_t> changed; //account changed bits
private:
	friend class boost::serialization::access;
	template<class Archive> void serialize(Archive & ar, const unsigned int version)
	{	
		ar & pk;
	 	ar & hash;
		//if(version>0) ar & msha;
		ar & msha;
	 	ar & msid;
	 	ar & mtim;
		ar & weight; //changed order !!!
	 	ar & status; //changed order !!!
	 	//if(version==0) ar & in;
	 	//if(version==0) ar & out;
	 	//if(version==0) ar & addr;
	 	//if(version==1) ar & host;
	 	//if(version==1) ar & port;
	 	////ar & status;
		//if(version>0) ar & weight;
		////ar & weight;
	 	if(version>1) ar & users;
	 	if(version>1) ar & port;
	 	if(version>1) ar & ipv4;
	}
};
BOOST_CLASS_VERSION(node, 2)
class servers // also a block
{
public:
	uint32_t now; // start time of the block
	//uint32_t old; // time of last block
	uint32_t txs; // number of transactions in block, FIXME, should be uint16_t
	uint32_t nod; // number of nodes in block, FIXME, should be uint16_t
	uint8_t oldhash[SHA256_DIGEST_LENGTH]; // previous hash
	uint8_t txshash[SHA256_DIGEST_LENGTH]; // hash of transactions
	uint8_t nodhash[SHA256_DIGEST_LENGTH]; // hash of nodes
	uint8_t nowhash[SHA256_DIGEST_LENGTH]; // current hash
	uint16_t vok;
	uint16_t vno;
	std::vector<node> nodes;
	//std::vector<void*> users; // place holder for user accounts
	servers():
		now(0),
		//old(0), //FIXME, remove, oldhash will be '000...000' if old!=now-BLOCKSEC
		txs(0),
		nod(0),
		oldhash{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
		txshash{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
		nodhash{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
		nowhash{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
		vok(0),
		vno(0) 
	{}

	int init(uint32_t newnow)
	{	uint16_t num=0;
		 int64_t stw=TOTALMASS/(nodes.size()-1);
		 int64_t sum=0;
                now=newnow;
		blockdir();
		for(auto it=nodes.begin();it<nodes.end();it++,num++){
			bzero(it->hash,SHA256_DIGEST_LENGTH);
			memset(it->msha,0xff,SHA256_DIGEST_LENGTH); //TODO, start servers this way too
			memcpy(it->msha,&num,2); // always start with a unique hash
			it->msid=0;
			it->mtim=now;
			it->status=0;
			if(num){
				if(num<=VIP_MAX){
					it->status|=SERVER_VIP;}
				it->users=1;
				//it->weight=stw-num;
				//add_user(num,0,it->weight,it->pk);
				// create the first user
				user_t u;
				init_user(u,num,0,stw-num,it->pk,now);
				put_user(u,num,0);
				update_nodehash(num);
				sum+=it->weight;}
			else{
				bzero(it->pk,SHA256_DIGEST_LENGTH);
				it->users=0;
				it->weight=0;}}
		nodes.begin()->weight=TOTALMASS-sum;
		assert(num>0);
		finish();
		return(num<VIP_MAX?num:VIP_MAX);
	}

	uint16_t add_node(user_t& ou,uint32_t uid)
	{	uint16_t peer=nodes.size();
		node nn;
		nn.mtim=now;
		nn.users=1;
		memcpy(nn.pk,ou.pkey,32);
		memset(nn.msha,0xff,SHA256_DIGEST_LENGTH); //TODO, start servers this way too
		memcpy(nn.msha,&peer,2); // always start with a unique hash
	 	nodes.push_back(nn);
		user_t nu;
		init_user(nu,peer,0,0,ou.pkey,now);
		nu.node=peer;
		nu.user=uid;
		put_user(nu,peer,0);
		//update_nodehash(peer);
		return(peer);
	}

	void put_node(user_t& ou,uint16_t peer,uint32_t uid)
	{	std::map<uint32_t,user_t> undo;
	 	user_t nu;
		get_user(nu,peer,0);
		undo[0]=nu;
		save_undo(peer,undo,0);
		init_user(nu,peer,0,nu.weight,ou.pkey,now);
		nu.node=peer;
		nu.user=uid;
		put_user(nu,peer,0);
		nodes[peer].mtim=now;
	}

	void init_user(user_t& u,uint16_t peer,uint32_t uid,int64_t weight,uint8_t* pk,uint32_t when)
	{	memset(&u,0,sizeof(user_t));
		memset(u.hash,0xff,SHA256_DIGEST_LENGTH); //TODO, start servers this way too
		memcpy(u.hash,&uid,4); // always start with a unique hash
		memcpy(u.hash+4,&peer,2); // always start with a unique hash
		u.msid=1; // always >0 to help identify holes in delta files
                u.time=when;
		u.node=peer;
		u.user=uid;
                u.lpath=when;
                u.rpath=when-START_AGE;
		u.weight=weight;
		memcpy(u.pkey,pk,SHA256_DIGEST_LENGTH);
        }

	void put_user(user_t& u,uint16_t peer,uint32_t uid)
	{	char filename[64];
		sprintf(filename,"usr/%04X.dat",peer);
		int fd=open(filename,O_WRONLY|O_CREAT,0644);
		if(fd<0){ std::cerr << "ERROR, failed to open account file "<<filename<<", fatal\n";
			exit(-1);}
		lseek(fd,uid*sizeof(user_t),SEEK_SET);
		write(fd,&u,sizeof(user_t));
		close(fd);
		if(nodes[peer].users<=uid){ //consider locking
			nodes[peer].users=uid+1;
			nodes[peer].changed.resize(1+uid/64);}
	}

	void get_user(user_t& u,uint16_t peer,uint32_t uid)
	{	char filename[64];
		sprintf(filename,"usr/%04X.dat",peer);
		int fd=open(filename,O_RDONLY);
		if(fd<0){ std::cerr << "ERROR, failed to open account file "<<filename<<", fatal\n";
			exit(-1);}
		lseek(fd,uid*sizeof(user_t),SEEK_SET);
		read(fd,&u,sizeof(user_t));
		close(fd);
	}

	bool check_user(uint16_t peer,uint32_t uid)
	{	if(peer>=nodes.size()){
			fprintf(stderr,"ERROR, bad user %04X:%08X (bad node)\n",peer,uid);
			return(false);}
	 	if(nodes[peer].users<=uid){
			fprintf(stderr,"ERROR, bad user %04X:%08X [%08X]\n",peer,uid,nodes[peer].users);
			return(false);}
		return(true);
	}

	bool find_key(uint8_t* pkey,uint8_t* skey)
	{	FILE* fp=fopen("key/key.txt","r");
		char line[128];
		if(fp==NULL){
			return(false);}
		while(fgets(line,128,fp)>0){
			if(line[0]=='#' || strlen(line)<64){
				continue;}
			hash_t pk;
			ed25519_text2key(skey,line,32); // do not send last hash
			ed25519_publickey(skey,pk);
			if(!memcmp(pkey,pk,32)){
				fclose(fp);
				return(true);}}
		fclose(fp);
		return(false);
	}

	void update_nodehash(uint16_t peer)
	{	assert(peer);
                char filename[64];
		sprintf(filename,"usr/%04X.dat",peer);
		int fd=open(filename,O_RDONLY);
		if(fd<0){ std::cerr << "ERROR, failed to open account file "<<filename<<", fatal\n";
			exit(-1);}
		SHA256_CTX sha256;
		SHA256_Init(&sha256);
		uint32_t end=nodes[peer].users;
		uint64_t weight=0;
		for(uint32_t i=0;i<end;i++){
			user_t u;
			if(sizeof(user_t)!=read(fd,&u,sizeof(user_t))){
				fprintf(stderr,"ERROR, failed to read %04X:%08X from %s\n",peer,i,filename);
				exit(-1);}
//FIXME, remove
        fprintf(stderr,"USER:%04X:%08X m:%08X t:%08X s:%04X b:%04X u:%08X l:%08X r:%08X v:%016lX\n",
          peer,i,u.msid,u.time,u.stat,u.node,u.user,u.lpath,u.rpath,u.weight);
			weight+=u.weight;
			SHA256_Update(&sha256,&u,sizeof(user_t));}
		SHA256_Final(nodes[peer].hash,&sha256);
		nodes[peer].weight=weight;
		close(fd);
	}

	void save_undo(uint16_t svid,std::map<uint32_t,user_t>& undo,uint32_t users)
	{	char filename[64];
		sprintf(filename,"blk/%08X/und/%04X.dat",now,svid);
		int fd=open(filename,O_WRONLY|O_CREAT,0644);
		if(fd<0){
			std::cerr<<"ERROR, failed to open bank undo "<<svid<<", fatal\n";
			exit(-1);}
		if(nodes[svid].users<=users){ //consider locking
			nodes[svid].users=users;
			nodes[svid].changed.resize(1+users/64);}
		for(auto it=undo.begin();it!=undo.end();it++){
			int i=(it->first)/64;
			int j=1<<((it->first)%64);
			if(nodes[svid].changed[i]&j){
				return;}
			nodes[svid].changed[i]|=j;
			lseek(fd,(it->first)*sizeof(user_t),SEEK_SET);
			write(fd,&it->second,sizeof(user_t));}
		close(fd);
	}

	void clear_undo()
	{	for(auto n=nodes.begin();n!=nodes.end();n++){
			n->changed.clear();}
	}

	void get()
	{	std::ifstream ifs("servers.txt");
		if(ifs.is_open()){
			boost::archive::text_iarchive ia(ifs);
			ia >> (*this);}
		std::cout << std::to_string((int)nodes.size()) << " servers loaded\n";
	}
	void get(uint32_t path)
	{	char filename[64];
	 	if(path==0){
			get();
			return;}
		sprintf(filename,"blk/%08X/servers.txt",path);
		std::ifstream ifs(filename);
		if(ifs.is_open()){
			boost::archive::text_iarchive ia(ifs);
			ia >> (*this);}
		assert(now==path);
		std::cout << std::to_string((int)nodes.size()) << " servers loaded\n";
	}
	void put() //uint32_t path) //FIXME, not used
	{	char filename[64]="servers.txt";
	 	if(now>0){
			sprintf(filename,"blk/%08X/servers.txt",now);}
		std::ofstream ofs(filename);
		if(ofs.is_open()){
			boost::archive::text_oarchive oa(ofs);
			oa << (*this);}
		else{
			std::cerr<<"ERROR, failed to write servers to dir:"<<now<<"\n";}

	}
	//void finish(uint32_t path,uint32_t txcount,uint8_t* txsh)
	//void finish(uint8_t* txsh)
	void hashtxs(std::map<uint64_t,message_ptr>& map)
	{	SHA256_CTX sha256;
		SHA256_Init(&sha256);
		int i=0;
		for(auto it=map.begin();it!=map.end();++it,i++){
			SHA256_Update(&sha256,it->second->sigh,SHA256_DIGEST_LENGTH);}
		txs=i;
		SHA256_Final(txshash, &sha256);
	}

	void txs_put(std::map<uint64_t,message_ptr>& map) // FIXME, add this also for std::map<uint64_t,message_ptr>
	{	hashtxs(map);
	 	char filename[64];
		sprintf(filename,"blk/%08X/txslist.dat",now); //FIXME, save in a file named based on txshash "blk/%08X/txs_%.64s.dat"
		int fd=open(filename,O_WRONLY|O_CREAT,0644);
		if(fd<0){ //trow or something :-)
			return;}
		write(fd,txshash,SHA256_DIGEST_LENGTH);
		for(auto it=map.begin();it!=map.end();it++){
			write(fd,&it->second->svid,2); // we must later distinguish between double spend and normal messages ... maybe using msid==0xffffffff;
			write(fd,&it->second->msid,4);
			write(fd,it->second->sigh,SHA256_DIGEST_LENGTH);} // maybe we have to set sigh=last_msg_sigh for double spend messages
		close(fd);
	}
	int txs_get(char* data)
	{	char filename[64];
		sprintf(filename,"blk/%08X/txslist.dat",now); //FIXME, save in a file named based on txshash "blk/%08X/txs_%.64s.dat"
		int fd=open(filename,O_RDONLY);
		if(fd<0){
			return(0);}
		int len=read(fd,data,SHA256_DIGEST_LENGTH+txs*(2+4+SHA256_DIGEST_LENGTH));
		close(fd);
		return(len);
	}
	void txs_map(char* data,std::map<uint64_t,message_ptr>& map,uint16_t mysvid)
	{	char* d=data+SHA256_DIGEST_LENGTH;
		for(uint16_t i=0;i<txs;i++,d+=2+4+SHA256_DIGEST_LENGTH){
			message_ptr msg(new message((uint16_t*)(d),(uint32_t*)(d+2),(char*)(d+6),mysvid,now)); // uint32_t alignment ???
			map[msg->hash.num]=msg;}
	}
	int load_txslist(std::map<uint64_t,message_ptr>& map,uint16_t mysvid)
	{	char* data=(char*)malloc(SHA256_DIGEST_LENGTH+txs*(2+4+SHA256_DIGEST_LENGTH));
		if(txs_get(data)!=(int)(SHA256_DIGEST_LENGTH+txs*(2+4+SHA256_DIGEST_LENGTH))){
			free(data);
			return(0);}
		if(memcmp(data,txshash,SHA256_DIGEST_LENGTH)){
			free(data);
			return(0);}
		txs_map(data,map,mysvid);
		free(data);
		return(1);
	}
	void finish()
	{	//now=path;
		//txstotal=txcount;
		//memcpy(txshash,txsh,sizeof(txshash));
		nod=nodes.size();
		SHA256_CTX sha256;
		SHA256_Init(&sha256);
		for(auto it=nodes.begin();it<nodes.end();it++){ // consider changing this to hashtree/hashcalendar
			fprintf(stderr,"NOD: %08x %08x %08x %08X %08X %u %016lX %u\n",
				(uint32_t)*((uint32_t*)&it->pk[0]),(uint32_t)*((uint32_t*)&it->hash[0]),(uint32_t)*((uint32_t*)&it->msha[0]),it->msid,it->mtim,it->status,it->weight,it->users);
			SHA256_Update(&sha256,it->pk,sizeof(ed25519_public_key));
			SHA256_Update(&sha256,it->hash,SHA256_DIGEST_LENGTH);
			SHA256_Update(&sha256,it->msha,SHA256_DIGEST_LENGTH);
			SHA256_Update(&sha256,&it->msid,sizeof(uint32_t));
			SHA256_Update(&sha256,&it->mtim,sizeof(uint32_t));
			SHA256_Update(&sha256,&it->status,sizeof(uint32_t));
			SHA256_Update(&sha256,&it->weight,sizeof(uint32_t));
			SHA256_Update(&sha256,&it->users,sizeof(uint32_t));}
		SHA256_Final(nodhash, &sha256);
		char hash[2*SHA256_DIGEST_LENGTH];
		ed25519_key2text(hash,nodhash,SHA256_DIGEST_LENGTH);
		fprintf(stderr,"NODHASH sync %.*s\n",2*SHA256_DIGEST_LENGTH,hash);
		hashnow();
		char filename[64];
		sprintf(filename,"blk/%08X/servers.txt",now);
		std::ofstream sfs(filename);
		boost::archive::text_oarchive sa(sfs);
		sa << (*this);
		header_put();
		/*sprintf(filename,"blk/%08X/header.txt",now);
		std::ofstream hfs(filename);
		boost::archive::text_oarchive ha(hfs);
		ha << now;
		ha << txs;
		ha << nod;
		ha << oldhash;
		ha << txshash;
		ha << nodhash;
		ha << nowhash;
		ha << vok;
		ha << vno;*/
		header_print();
		clear_undo();
	}
	void hashnow()
	{	SHA256_CTX sha256;
	 	SHA256_Init(&sha256);
		SHA256_Update(&sha256,&now,sizeof(uint32_t));
		SHA256_Update(&sha256,&txs,sizeof(uint32_t));
		SHA256_Update(&sha256,&nod,sizeof(uint32_t));
		SHA256_Update(&sha256,oldhash,SHA256_DIGEST_LENGTH);
		SHA256_Update(&sha256,txshash,SHA256_DIGEST_LENGTH);
		SHA256_Update(&sha256,nodhash,SHA256_DIGEST_LENGTH);
		SHA256_Final(nowhash, &sha256);
	}
	void loadlink(headlink_t& link,uint32_t path,char* oldh)
	{	now=path;
		txs=link.txs;
		nod=link.nod;
		memcpy(oldhash,oldh,SHA256_DIGEST_LENGTH);
		memcpy(txshash,link.txshash,SHA256_DIGEST_LENGTH);
		memcpy(nodhash,link.nodhash,SHA256_DIGEST_LENGTH);
		hashnow();
	}
	void filllink(headlink_t& link)//write to link
	{	link.txs=txs;
		link.nod=nod;
		memcpy(link.txshash,txshash,SHA256_DIGEST_LENGTH);
		memcpy(link.nodhash,nodhash,SHA256_DIGEST_LENGTH);
	}
	void loadhead(header_t& head)
	{	now=head.now;
		txs=head.txs;
		nod=head.nod;
		memcpy(oldhash,head.oldhash,SHA256_DIGEST_LENGTH);
		memcpy(txshash,head.txshash,SHA256_DIGEST_LENGTH);
		memcpy(nodhash,head.nodhash,SHA256_DIGEST_LENGTH);
		hashnow();
	}
	void header(header_t& head)//change this to fill head
	{	head.now=now;
		head.txs=txs;
		head.nod=nod;
		memcpy(head.oldhash,oldhash,SHA256_DIGEST_LENGTH);
		memcpy(head.txshash,txshash,SHA256_DIGEST_LENGTH);
		memcpy(head.nodhash,nodhash,SHA256_DIGEST_LENGTH);
		memcpy(head.nowhash,nowhash,SHA256_DIGEST_LENGTH);
		head.vok=vok;
		head.vno=vno;
	}
	int header_get()
	{	char filename[64];
		sprintf(filename,"blk/%08X/header.txt",now);
		std::ifstream ifs(filename);
		uint32_t check=0;
		if(ifs.is_open()){
			boost::archive::text_iarchive ia(ifs);
			ia >> check;
			ia >> txs;
			ia >> nod;
			ia >> oldhash;
			ia >> txshash;
			ia >> nodhash;
			ia >> nowhash;
			ia >> vok;
			ia >> vno;}
		else{
			std::cerr<<"ERROR, failed to read header\n";
			return(0);}
		if(check!=now){
			std::cerr<<"ERROR, failed to check header\n";
			return(0);}
		return(1);
	}
	void header_put()
	{	char filename[64];
		sprintf(filename,"blk/%08X/header.txt",now);
		std::ofstream ofs(filename);
		if(ofs.is_open()){
			boost::archive::text_oarchive oa(ofs);
			oa << now;
			oa << txs;
			oa << nod;
			oa << oldhash;
			oa << txshash;
			oa << nodhash;
			oa << nowhash;
			oa << vok;
			oa << vno;}
		else{
			std::cerr<<"ERROR, failed to write header\n";}
	}
	void header_print()
	{	char hash[2*SHA256_DIGEST_LENGTH];
	 	fprintf(stderr,"HEAD now:%08X txs:%08X nod:%08X vok:%04X vno:%04X\n",now,txs,nod,vok,vno);
		ed25519_key2text(hash,oldhash,SHA256_DIGEST_LENGTH);
		fprintf(stderr,"OLDHASH %.*s\n",2*SHA256_DIGEST_LENGTH,hash);
		ed25519_key2text(hash,txshash,SHA256_DIGEST_LENGTH);
		fprintf(stderr,"TXSHASH %.*s\n",2*SHA256_DIGEST_LENGTH,hash);
		ed25519_key2text(hash,nodhash,SHA256_DIGEST_LENGTH);
		fprintf(stderr,"NODHASH %.*s\n",2*SHA256_DIGEST_LENGTH,hash);
		ed25519_key2text(hash,nowhash,SHA256_DIGEST_LENGTH);
		fprintf(stderr,"NOWHASH %.*s\n",2*SHA256_DIGEST_LENGTH,hash);
	}
	void header_print(header_t& head)
	{	char hash[2*SHA256_DIGEST_LENGTH];
	 	fprintf(stderr,"HEAD now:%08X txs:%08X nod:%08X vok:%04X vno:%04X\n",head.now,head.txs,head.nod,head.vok,head.vno);
		ed25519_key2text(hash,head.oldhash,SHA256_DIGEST_LENGTH);
		fprintf(stderr,"OLDHASH %.*s\n",2*SHA256_DIGEST_LENGTH,hash);
		ed25519_key2text(hash,head.txshash,SHA256_DIGEST_LENGTH);
		fprintf(stderr,"TXSHASH %.*s\n",2*SHA256_DIGEST_LENGTH,hash);
		ed25519_key2text(hash,head.nodhash,SHA256_DIGEST_LENGTH);
		fprintf(stderr,"NODHASH %.*s\n",2*SHA256_DIGEST_LENGTH,hash);
		ed25519_key2text(hash,head.nowhash,SHA256_DIGEST_LENGTH);
		fprintf(stderr,"NOWHASH %.*s\n",2*SHA256_DIGEST_LENGTH,hash);
	}
	void save_signature(uint16_t svid,uint8_t* sig,bool ok)
	{	char filename[64];
		sprintf(filename,"blk/%08X/signatures.txt",now); //FIXME, save in a file named based on nowhash "blk/%08X/sig_%.64s.txt"
		char hash[4*SHA256_DIGEST_LENGTH];
		ed25519_key2text(hash,sig,2*SHA256_DIGEST_LENGTH);
		//mtx_.lock();
		FILE *fp=fopen(filename,"a");
		fprintf(fp,"%04X\t%.*s\t%d\n",(uint32_t)svid,4*SHA256_DIGEST_LENGTH,hash,ok);
		fclose(fp);
		//mtx_.unlock();
		//assert(vok+vno<VIP_MAX-1);
		if(ok){
			std::cerr << "BLOCK ok\n";
			vok++;
			sprintf(filename,"blk/%08X/signatures.ok",now);}
		else{
//FIXME, no point to save no signatures without corresponding block
			std::cerr << "BLOCK differs\n";
			vno++;
			sprintf(filename,"blk/%08X/signatures.no",now);}
		svsi_t da;
		memcpy(da,&svid,2);
		memcpy(da+2,sig,2*SHA256_DIGEST_LENGTH);
		int fd=open(filename,O_WRONLY|O_CREAT|O_APPEND,0644);
                write(fd,da,sizeof(svsi_t));
		close(fd);
	}
	void get_signatures(uint32_t path,uint8_t* data,int nok,int nno) // does not use any local data
	{	int fd;
		char filename[64];
		sprintf(filename,"blk/%08X/signatures.ok",path);
		fd=open(filename,O_RDONLY);
		if(fd>=0){
			read(fd,data,sizeof(svsi_t)*nok);
			//vok=read(fd,ok,sizeof(svsi_t)*VIP_MAX);
			//vok/=sizeof(svsi_t);
			close(fd);}
		sprintf(filename,"blk/%08X/signatures.no",path);
		fd=open(filename,O_RDONLY);
		if(fd>=0){
			read(fd,data+sizeof(svsi_t)*nok,sizeof(svsi_t)*nno);
			//vno=read(fd,ok,sizeof(svsi_t)*VIP_MAX);
			//vno/=sizeof(svsi_t);
			close(fd);}
	}
	void put_signatures(header_t& head,svsi_t* svsi) //FIXME, save in a file named based on nowhash "blk/%08X/sig_%.64s.dat"
	{	int fd;
		char filename[64];
		sprintf(filename,"blk/%08X/signatures.ok",head.now);
		fd=open(filename,O_WRONLY|O_CREAT,0644);
		if(fd>=0){
			write(fd,&svsi[0],sizeof(svsi_t)*head.vok);
			close(fd);}
		sprintf(filename,"blk/%08X/signatures.no",head.now);
		fd=open(filename,O_WRONLY|O_CREAT,0644);
		if(fd>=0){
			write(fd,&svsi[head.vok],sizeof(svsi_t)*head.vno);
			close(fd);}
	}
	void check_signatures(header_t& head,svsi_t* svsi)
	{	int i=0,j=0;
		for(;i<head.vok;i++){
			uint8_t* data=(uint8_t*)&svsi[i];
			uint16_t svid;
			memcpy(&svid,data,2);
			if(svid>=nodes.size()){
				continue;}
			int error=ed25519_sign_open((const unsigned char*)&head,sizeof(header_t)-4,nodes[svid].pk,data+2);
			if(error){
				char hash[4*SHA256_DIGEST_LENGTH];
				ed25519_key2text(hash,data+2,2*SHA256_DIGEST_LENGTH);
				fprintf(stderr,"BLOCK SIGNATURE failed %.*s (%d)\n",4*SHA256_DIGEST_LENGTH,hash,svid);
				header_print(head);
				continue;}
			if(j!=i){
				memcpy(svsi+j,svsi+i,sizeof(svsi_t));}
			j++;}
		head.vno+=head.vok;
		head.vok=j;
//FIXME, no point to send or check no-signatures ... they will never validate
		for(;i<head.vno;i++){
			uint8_t* data=(uint8_t*)&svsi[i];
			uint16_t svid;
			memcpy(&svid,data,2);
			if(svid>=nodes.size()){
				continue;}
			int error=ed25519_sign_open((const unsigned char*)&head,sizeof(header_t)-4,nodes[svid].pk,data+2);
			if(error){
				continue;}
			if(j!=i){
				memcpy(svsi+j,svsi+i,sizeof(svsi_t));}
			j++;}
		head.vno=j-head.vok;
          	std::cerr << "READ block signatures confirmed:"<<head.vok<<" failed:"<<head.vno<<"\n";
	}

	bool copy_nodes(node_t* peer_node,uint16_t peer_srvn)
	{	uint32_t i;
		if(nodes.size()!=peer_srvn){
			return(false);}
		//FIXME, we should lock this somehow :-(, then the for test could be faster
		for(i=0;i<peer_srvn&&i<nodes.size();i++){
			memcpy(peer_node[i].pk,nodes[i].pk,sizeof(ed25519_public_key));
			memcpy(peer_node[i].hash,nodes[i].hash,SHA256_DIGEST_LENGTH);
			memcpy(peer_node[i].msha,nodes[i].msha,SHA256_DIGEST_LENGTH);
			peer_node[i].msid=nodes[i].msid;
			peer_node[i].mtim=nodes[i].mtim;
			peer_node[i].status=nodes[i].status;
			peer_node[i].weight=nodes[i].weight;
			peer_node[i].users=nodes[i].users;
			peer_node[i].port=nodes[i].port;
			peer_node[i].ipv4=nodes[i].ipv4;}
	 	if(nodes.size()!=i){
			return(false);}
		return(true);
	}

	//FIXME, run this check on local nodes; do not load as parameter
	int check_nodes(node_t* peer_node,uint16_t peer_srvn,uint8_t* peer_nodehash)
	{	int i=0;
		for(auto no=nodes.begin();no!=nodes.end()&&i<peer_srvn;no++,i++){
			if(memcmp(no->pk,peer_node[i].pk,sizeof(ed25519_public_key))){
				std::cerr<<"WARNING key mismatch for peer "<<i<<"\n";}}
		uint8_t tmphash[SHA256_DIGEST_LENGTH]; // hash of nodes
		SHA256_CTX sha256;
		SHA256_Init(&sha256);
		for(i=0;i<peer_srvn;i++){ // consider changing this to hashtree/hashcalendar
			fprintf(stderr,"NOD: %08x %08x %08x %08X %08X %u %016lX %u\n",
				(uint32_t)*((uint32_t*)&peer_node[i].pk[0]),(uint32_t)*((uint32_t*)&peer_node[i].hash[0]),(uint32_t)*((uint32_t*)&peer_node[i].msha[0]),
				peer_node[i].msid,peer_node[i].mtim,peer_node[i].status,peer_node[i].weight,peer_node[i].users);
			SHA256_Update(&sha256,peer_node[i].pk,sizeof(ed25519_public_key));
			SHA256_Update(&sha256,peer_node[i].hash,SHA256_DIGEST_LENGTH);
			SHA256_Update(&sha256,peer_node[i].msha,SHA256_DIGEST_LENGTH);
			SHA256_Update(&sha256,&peer_node[i].msid,sizeof(uint32_t));
			SHA256_Update(&sha256,&peer_node[i].mtim,sizeof(uint32_t));
			SHA256_Update(&sha256,&peer_node[i].status,sizeof(uint32_t));
			SHA256_Update(&sha256,&peer_node[i].weight,sizeof(uint32_t));
			SHA256_Update(&sha256,&peer_node[i].users,sizeof(uint32_t));}
		SHA256_Final(tmphash, &sha256);
		char hash[2*SHA256_DIGEST_LENGTH];
		ed25519_key2text(hash,tmphash,SHA256_DIGEST_LENGTH);
		fprintf(stderr,"NODHASH sync %.*s\n",2*SHA256_DIGEST_LENGTH,hash);
		return(memcmp(tmphash,peer_nodehash,SHA256_DIGEST_LENGTH));
	}

	void overwrite(header_t& head,node_t* peer_node)
	{ 	now=head.now;
		txs=head.txs;
		nod=head.nod;
		memcpy(oldhash,head.oldhash,SHA256_DIGEST_LENGTH);
		memcpy(txshash,head.txshash,SHA256_DIGEST_LENGTH);
		memcpy(nodhash,head.nodhash,SHA256_DIGEST_LENGTH);
		memcpy(nowhash,head.nowhash,SHA256_DIGEST_LENGTH);
		vok=head.vok;
		vno=head.vno;
		if(nodes.size()<nod){
			nodes.resize(nod);}
		for(uint32_t i=0;i<nod;i++){ // consider changing this to hashtree/hashcalendar
			memcpy(nodes[i].pk,peer_node[i].pk,sizeof(ed25519_public_key));
			memcpy(nodes[i].hash,peer_node[i].hash,SHA256_DIGEST_LENGTH);
			memcpy(nodes[i].msha,peer_node[i].msha,SHA256_DIGEST_LENGTH);
			nodes[i].msid=peer_node[i].msid;
			nodes[i].mtim=peer_node[i].mtim;
			nodes[i].status=peer_node[i].status;
			nodes[i].weight=peer_node[i].weight;
			nodes[i].users=peer_node[i].users;
			nodes[i].port=peer_node[i].port;
			nodes[i].ipv4=peer_node[i].ipv4;}
			//nodes[i].addr; // keep as before
			//nodes[i].host[64]; // keep as before
			//nodes[i].port; // keep as before
			//nodes[i].in.clear(); // incomming connections
			//nodes[i].out.clear(); // outgoing connections
	}

	int update_vip()
	{	uint32_t i;
		vok=0;
		vno=0;
		std::vector<uint16_t> svid_rank;
		for(i=0;i<nodes.size();i++){
			if(nodes[i].status & SERVER_DBL){
				continue;}
			nodes[i].status &= ~SERVER_VIP;
			svid_rank.push_back(i);}
		std::sort(svid_rank.begin(),svid_rank.end(),[this](const uint16_t& i,const uint16_t& j){return(this->nodes[i].weight>this->nodes[j].weight);}); //fuck, lambda :-(
		for(i=0;i<VIP_MAX&&i<svid_rank.size();i++){
			nodes[svid_rank[i]].status |= SERVER_VIP;}
		assert(i>0);
		return(i<VIP_MAX?i:VIP_MAX);
	}

	void blockdir() //not only dir ... should be called blockstart
	{	char pathname[16];
		sprintf(pathname,"blk/%08X",now);
		mkdir(pathname,0755);
		sprintf(pathname,"blk/%08X/und",now);
		mkdir(pathname,0755);
		for(auto n=nodes.begin();n!=nodes.end();n++){
			n->changed.resize(1+n->users/64);}
		sprintf(pathname,"blk/%08X",now+BLOCKSEC); // to make space for moved files
		mkdir(pathname,0755);
	}

private:
	//boost::mutex mtx_; can not be coppied :-(
	friend class boost::serialization::access;
	template<class Archive> void serialize(Archive & ar, const unsigned int version)
	{	//uint32_t old; // remove later
		ar & now;
		//if(version>0) ar & old;
		//if(version>0) ar & txs;
		//if(version==1) ar & old;
		//if(version==1) ar & txs;
		if(version>2) ar & txs;
		if(version>2) ar & nod;
		if(version>0) ar & oldhash;
		if(version>1) ar & txshash;
		if(version>1) ar & nodhash;
		if(version>0) ar & nowhash;
		if(version>2) ar & vok;
		if(version>2) ar & vno;
		ar & nodes;
	}
};
BOOST_CLASS_VERSION(servers, 3)

#endif // SERVERS_HPP
