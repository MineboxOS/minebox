package io.minebox.siaseed;

import com.google.common.collect.ImmutableList;

import java.util.List;

public class MoneroSeed {

    public List<String> mn_encode(String str) {
        ImmutableList.Builder<String> b = ImmutableList.builder();

        int dictSize = EngDict.size;
        final ImmutableList<String> dict = EngDict.dict;

        for (int j = 0; j < str.length(); j += 8) {
            str = str.substring(0, j) + mn_swap_endian_4byte(str.substring(j, j + 8)) + str.substring(j + 8);
        }

        for (int i = 0; i < str.length(); i += 8) {
            int x = Integer.parseInt(str.substring(i, 8), 16);
            final double divX = Math.floor((double) x / dictSize);
            int w1 = (x % dictSize);
            int w2 = ((int) divX + w1) % dictSize;
            int w3 = ((int) Math.floor(divX / dictSize) + w2) % dictSize;
            b.add(dict.get(w1), dict.get(w2), dict.get(w3));
        }

       /* if (EngDict.ENGLISH_UNIQUE_PREFIX_LEN > 0) {
            out.push(out[mn_get_checksum_index(out, wordset.prefix_len)]);
        }
        return out.join(' ');*/
       return null;
    }

    public String mn_swap_endian_4byte(String str) {
        if (str.length() != 8) throw new RuntimeException("Invalid input length: " + str.length());
        return str.substring(6, 8) + str.substring(4, 6) + str.substring(2, 4) + str.substring(0, 2);
    }

    /*public byte[] mn_decode(str) {
        'use strict';
        wordset_name = wordset_name || mn_default_wordset;
        var wordset = mn_words[wordset_name];
        var out = '';
        var n = wordset.words.length;
        var wlist = str.split(' ');
        var checksum_word = '';
        if (wlist.length < 12) throw "You've entered too few words, please try again";
        if ((wordset.prefix_len === 0 && (wlist.length % 3 !== 0)) ||
                (wordset.prefix_len > 0 && (wlist.length % 3 === 2))) throw "You've entered too few words, please try again";
        if (wordset.prefix_len > 0 && (wlist.length % 3 === 0)) throw "You seem to be missing the last word in your private key, please try again";
        if (wordset.prefix_len > 0) {
            // Pop checksum from mnemonic
            checksum_word = wlist.pop();
        }
        // Decode mnemonic
        for (var i = 0; i < wlist.length; i += 3) {
            var w1, w2, w3;
            if (wordset.prefix_len === 0) {
                w1 = wordset.words.indexOf(wlist[i]);
                w2 = wordset.words.indexOf(wlist[i + 1]);
                w3 = wordset.words.indexOf(wlist[i + 2]);
            } else {
                w1 = wordset.trunc_words.indexOf(wlist[i].substring(0, wordset.prefix_len));
                w2 = wordset.trunc_words.indexOf(wlist[i + 1].substring(0, wordset.prefix_len));
                w3 = wordset.trunc_words.indexOf(wlist[i + 2].substring(0, wordset.prefix_len));
            }
            if (w1 === -1 || w2 === -1 || w3 === -1) {
                throw "invalid word in mnemonic";
            }
            var x = w1 + n * (((n - w1) + w2) % n) + n * n * (((n - w2) + w3) % n);
            if (x % n != w1) throw 'Something went wrong when decoding your private key, please try again';
            out += mn_swap_endian_4byte(('0000000' + x.toString(16)).substring(-8));
        }
        // Verify checksum
        if (wordset.prefix_len > 0) {
            var index = mn_get_checksum_index(wlist, wordset.prefix_len);
            var expected_checksum_word = wlist[index];
            if (expected_checksum_word.substring(0, wordset.prefix_len) !== checksum_word.substring(0, wordset.prefix_len)) {
                throw "Your private key could not be verified, please try again";
            }
        }
        return out;
    }*/

}
