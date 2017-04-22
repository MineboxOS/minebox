///*
//package io.minebox.siaseed;
//
//import com.fasterxml.jackson.databind.node.BigIntegerNode;
//
//import java.math.BigInteger;
//import java.util.List;
//
//public class Base1626 {
//
//
//    // DictionarySize specifies the size of the dictionaries that are used by
//    // the mnemonics package. All dictionaries are the same length so that the
//    // same []byte can be encoded into multiple languages and all results will
//    // resemble eachother.
//   int DictionarySize = 1626;
//
//// The conversion functions can be seen as changing the base of a number. A
//// []byte can actually be viewed as a slice of base-256 numbers, and a []dict
//// can be viewed as a slice of base-1626 numbers. The conversions are a little
//// strange because leading 0's need to be preserved.
////
//// For example, in base 256:
////
////		{0} -> 0
////		{255} -> 255
////		{0, 0} -> 256
////		{1, 0} -> 257
////		{0, 1} -> 512
//
//
//    // phraseToInt coverts a phrase into a big.Int, using logic similar to
//// bytesToInt.
//    public BigInteger phraseToInt(List<String> phrase) {
//
//
//        BigInteger base = BigInteger.valueOf(1626);
//        BigInteger exp = BigInteger.valueOf(1);
//        BigInteger result = BigInteger.valueOf(-1);
//        for _, word := range p {
//            // Normalize the input.
//            word = norm.NFC.String(word)
//
//            // Get the first prefixLen runes from the string.
//            var prefix []byte
//            var runeCount int
//            for _, r := range word {
//                encR := make([]byte, utf8.RuneLen(r))
//                utf8.EncodeRune(encR, r)
//                prefix = append(prefix, encR...)
//
//                runeCount++
//                if runeCount == prefixLen {
//                    break
//                }
//            }
//
//            // Find the index associated with the phrase.
//            var tmp *big.Int
//            found := false
//            for j, word := range dict {
//                if strings.HasPrefix(word, string(prefix)) {
//                    tmp = big.NewInt(int64(j))
//                    found = true
//                    break
//                }
//            }
//            if !found {
//                return nil, errUnknownWord
//            }
//
//            // Add the index to the int.
//            tmp.Add(tmp, big.NewInt(1))
//            tmp.Mul(tmp, exp)
//            exp.Mul(exp, base)
//            result.Add(result, tmp)
//        }
//        return result, nil
//    }
//
//    // intToPhrase converts a phrase into a big.Int, working in a fashion similar
//// to bytesToInt.
//    func intToPhrase(bi *big.Int, did DictionaryID) (p Phrase, err error) {
//        // Determine which dictionary to use based on the input language.
//        var dict Dictionary
//        switch {
//            case did == English:
//                dict = englishDictionary
//            case did == German:
//                dict = germanDictionary
//            case did == Japanese:
//                dict = japaneseDictionary
//            default:
//                return nil, errUnknownDictionary
//        }
//
//        base := big.NewInt(DictionarySize)
//        for bi.Cmp(base) >= 0 {
//            i := new(big.Int).Mod(bi, base).Int64()
//            p = append(p, dict[i])
//            bi.Sub(bi, base)
//            bi.Div(bi, base)
//        }
//        p = append(p, dict[bi.Int64()])
//        return p, nil
//    }
//
//    // ToPhrase converts an input []byte to a human-friendly phrase. The conversion
//// is reversible.
//    func ToPhrase(entropy []byte, did DictionaryID) (Phrase, error) {
//        if len(entropy) == 0 {
//            return nil, errEmptyInput
//        }
//        intEntropy := bytesToInt(entropy)
//        return intToPhrase(intEntropy, did)
//    }
//
//    // FromPhrase converts an input phrase back to the original []byte.
//    func FromPhrase(p Phrase, did DictionaryID) ([]byte, error) {
//        if len(p) == 0 {
//            return nil, errEmptyInput
//        }
//
//        intEntropy, err := phraseToInt(p, did)
//        if err != nil {
//            return nil, err
//        }
//        return intToBytes(intEntropy), nil
//    }
//
//    // FromString converts an input string into a phrase, and then calls
//// 'FromPhrase'.
//    func FromString(str string, did DictionaryID) ([]byte, error) {
//        phrase := Phrase(strings.Split(str, " "))
//        return FromPhrase(phrase, did)
//    }
//
//    // String combines a phrase into a single string by concatenating the
//// individual words with space separation.
//    func (p Phrase) String() string {
//        return strings.Join(p, " ")
//    }
//}
//*/
