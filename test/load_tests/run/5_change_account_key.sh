echo Valid: $Valid
echo esc: $esc
echo user_x: $user_x
echo
echo _address_x: $_address_x
echo _secret_x: $_secret_x
echo _public_key_x: $_public_key_x
echo _sign_x: $_sign_x

echo
cd $esc

echo ------------------------------ change_account_key -------------------------------
(echo '{"run":"get_me"}'; echo '{"run":"change_account_key","pkey":"'$_public_key_x'","signature":"'$_sign_x'"}') | ./esc -n$Valid  -P9001 -Hesc.dock -A$_address_x -s$_secret_x
