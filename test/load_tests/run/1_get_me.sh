echo Valid: $Valid
echo esc: $esc
echo user_x: $user_x
echo
echo _address_x: $_address_x
echo _secret_x: $_secret_x

echo
cd
cd ../..
cd $esc

echo ------------------------------ get_me -------------------------------
echo '{"run":"get_me"}' | ./esc -n$Valid  -P9001 -Hesc.dock -A$_address_x -s$_secret_x

