package net.adshares.esc.qa.data;

import com.google.gson.Gson;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class UserDataProvider {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String GENESIS_FILE = "genesis.json";
    private static final int STARTING_PORT_INT = 9001;
    private static final String HOST = "esc.dock";

    private static UserDataProvider instance;
    private List<UserData> users;

    private UserDataProvider() {

    }

    public static UserDataProvider getInstance() {
        if (instance == null) {
            instance = new UserDataProvider();
            instance.init();
        }
        return instance;
    }

    /**
     * Reads accounts data from genesis file.
     */
    private void init() {
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new FileReader(GENESIS_FILE));
        } catch (FileNotFoundException e) {
            log.error(e.toString());
        }

        Assert.assertNotNull("Cannot open network definition file.", bufferedReader);

        Gson gson = new Gson();
        Genesis json = gson.fromJson(bufferedReader, Genesis.class);

        users = new ArrayList<>();

        int portInt = STARTING_PORT_INT;
        for (Genesis.Node gn : json.getNodes()) {
            String port = String.valueOf(portInt);
            for (Genesis.Node.Account acc : gn.getAccounts()) {
                UserData user = new UserData(port, HOST, acc.getAddress(), acc.getSecret());
                users.add(user);
            }
            portInt++;
        }
    }

    public List<UserData> getUserDataList() {
        return getUserDataList(users.size());
    }

    public List<UserData> getUserDataList(int count) {
        return getUserDataList(count, false);
    }

    public List<UserData> getUserDataList(int count, boolean singleNode) {
        log.debug("getUserDataList(count=" + count + ", singleNode=" + singleNode + ")");

        ArrayList<UserData> userData = new ArrayList<>(count);
        UserData firstUser = null;
        for (UserData user : users) {
            if (singleNode) {
                if (firstUser == null) {
                    firstUser = user;
                    userData.add(firstUser);
                } else {
                    if (firstUser.isAccountFromSameNode(user.getAddress())) {
                        userData.add(user);
                    }
                }
            } else {
                userData.add(user);
            }

            if (userData.size() == count) {
                break;
            }
        }

        boolean enoughUsers = count == userData.size();
        if (!enoughUsers) {
            log.error("getUserDataList(count=" + count + ", singleNode=" + singleNode + ")");
            log.error("Not enough users. Needed {}, but only {} available.", count, userData.size());
        }
        Assert.assertTrue(enoughUsers);

        return userData;
    }

    public List<UserData> getUserDataFromDifferentNodes(int count) {
        log.debug("getUserDataFromDifferentNodes(count=" + count + ")");

        ArrayList<UserData> userData = new ArrayList<>(count);
        for (UserData user : users) {
            String userAddress = user.getAddress();
            boolean isSameNode = false;
            for (UserData addedUser : userData) {
                isSameNode = addedUser.isAccountFromSameNode(userAddress);
                if (isSameNode) {
                    break;
                }
            }

            if (!isSameNode) {
                userData.add(user);
            }

            if (userData.size() == count) {
                break;
            }
        }

        boolean enoughUsers = count == userData.size();
        if (!enoughUsers) {
            log.error("getUserDataFromDifferentNodes(count=" + count + ")");
            log.error("Not enough users. Needed {}, but only {} available.", count, userData.size());
        }
        Assert.assertTrue(enoughUsers);

        return userData;
    }

}
