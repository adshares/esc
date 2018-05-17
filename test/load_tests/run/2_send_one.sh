echo Valid: $Valid
echo esc: $esc
echo user_x: $user_x
echo user_y: $user_y
echo
echo _address_x: $_address_x
echo _secret_x: $_secret_x
echo _address_x: $_address_y
echo _secret_x: $_secret_y

echo
cd $esc

echo ------------------------------ send_one -------------------------------
(echo '{"run":"get_me"}';echo '{"run":"send_one", "address":"'$_address_x'", "amount":"100"}') | ./esc -n$Valid  -P9001 -Hesc.dock -A$_address_y -s$_secret_y

