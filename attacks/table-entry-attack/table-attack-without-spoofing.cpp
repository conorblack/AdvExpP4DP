/*
 * Copyright 2020 Conor Black - Queen's University Belfast
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define _GNU_SOURCE
#include <dlfcn.h>
#include <iostream>
#include <vector>
#include <fstream>
#include <algorithm>
#include <sstream>
#include <iostream>
#include <iterator>
#include <iomanip>
#include "/home/sdn/bmv2/include/bm/bm_sim/match_tables.h"
#include "/home/sdn/bmv2/include/bm/bm_sim/match_error_codes.h"
#include "/home/sdn/bmv2/include/bm/bm_sim/data.h"
#include "/home/sdn/bmv2/include/bm/bm_sim/context.h"
#include "/home/sdn/bmv2_install/include/PI/target/pi_imp.h"
#include "/home/sdn/bmv2_install/include/PI/int/pi_int.h"
#include "/home/sdn/bmv2_install/include/PI/pi_tables.h"
#include "/home/sdn/.cache/bazel/_bazel_sdn/777258622d61698847b000808f9af013/external/com_github_p4lang_PI_np4/proto/p4info/PI/proto/p4info_to_and_from_proto.h"
#include "/home/sdn/.cache/bazel/_bazel_root/777258622d61698847b000808f9af013/external/com_google_protobuf/src/google/protobuf/text_format.h"
#include "/home/sdn/.cache/bazel/_bazel_root/777258622d61698847b000808f9af013/external/com_github_p4lang_PI/targets/bmv2/common.h"

typedef bm::MatchErrorCode (bm::MatchTable::*original_add_entry_def) (const std::vector<bm::MatchKeyParam> &match_key, const bm::ActionFn *action_fn, bm::ActionData action_data, bm::entry_handle_t *handle, int priority);

static int original_id_value = -1;
static int first_id_value = -1;
static bool already_assigned = false;


bm::MatchErrorCode bm::MatchTable::add_entry(const std::vector<bm::MatchKeyParam> &match_key, const bm::ActionFn *action_fn, bm::ActionData action_data, bm::entry_handle_t *handle, int priority) {

     int* action_fn_address = (int*) action_fn;
     int* action_fn_address_1 = action_fn_address + 2;
     int* action_name_1 = * (int**) action_fn_address_1;
     char* char_pointer = (char*) action_name_1;
     std::string string_char = char_pointer;

      std::string match_key_string = match_key.at(0).key;
      std::ostringstream result;
      result << std::hex;
      std::copy(match_key_string.begin(), match_key_string.end(), std::ostream_iterator<unsigned int>(result));
      bool comparison = result.str().compare("ffffffc0ffffffa817a");

    if(string_char.compare("IngressPipeImpl.id_found") == 0 && action_data.size() == 1 && comparison != 0 && !already_assigned) {
        first_id_value = action_data.get(0).get_int();
        already_assigned = true;
    } else if(string_char.compare("IngressPipeImpl.id_found") == 0 && action_data.size() == 1 && comparison == 0 && already_assigned) {
        original_id_value = action_data.get(0).get_int();
        action_data.action_data.at(0) = (bm::Data) first_id_value;
    }

   static original_add_entry_def original_add_entry = 0;
    void* temp_ptr = dlsym(RTLD_NEXT,"_ZN2bm10MatchTable9add_entryERKSt6vectorINS_13MatchKeyParamESaIS2_EEPKNS_8ActionFnENS_10ActionDataEPji");
    memcpy(&original_add_entry, &temp_ptr, sizeof(&temp_ptr));
    bm::MatchErrorCode return_code = (this->*original_add_entry) (match_key, action_fn, action_data, handle, priority);
    return return_code;

}
