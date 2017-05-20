function PasswordCheck(data) {



    var CONFIG = {
        requirements: {
            min: 10, //minimum required characters (false if disabled)
            max: false, //maximum required characters (false if disabled)
            capitals: true, //required at least one capital letters (true or false)
            numbers: true, //required at least one number (true or false)
            specialChars: true, //required at least one special character (true or false)
            strength: 80 //required passwords strength (false if disabled)
        },
        messages: {
            min: 'Your password does not contain the minimum characters.',
            max: 'Your password is longer than the maximum characters allowed.',
            capitals: 'Your password does not contain any capital letter.',
            numbers: 'Your password does not contain any numbers.',
            specialChars: 'Your password does not contain any special characters.',
            strength: 'Your password does not meet the required strength.'
        }
    };
    //extend CONFIG properties for instanced PasswordCheck func
    $.extend(CONFIG, data, true);

    var response = {};

    function validate(password) {

        //checking if minimum chars
        if ( CONFIG.requirements.min ) {
            if ( !minimumCharacters(password) ) {
                response.min = {
                    validated: false,
                    message: CONFIG.messages.min
                }
            } else {
                response.min = {
                    validated: true
                }
            }
        }

        //checking if maximum chars
        if ( CONFIG.requirements.max ) {
            if ( !maximumCharacters(password) ) {
                response.max = {
                    validated: false,
                    message: CONFIG.messages.max
                }
            } else {
                response.max = {
                    validated: true
                }
            }
        }

        //checking if capital chars
        if ( CONFIG.requirements.capitals ) {
            if ( !containsCapitalLetters(password) ) {
                response.capitals = {
                    validated: false,
                    message: CONFIG.messages.capitals
                }
            } else {
                response.capitals = {
                    validated: true
                }
            }
        }

        //checking if numeric chars
        if ( CONFIG.requirements.numbers ) {
            if ( !containsNumber(password) ) {
                response.numbers = {
                    validated: false,
                    message: CONFIG.messages.numbers
                }
            } else {
                response.numbers = {
                    validated: true
                }
            }
        }

        //checking if special chars
        if ( CONFIG.requirements.specialChars ) {
            if ( !specialCharacters(password) ) {
                response.specialChars = {
                    validated: false,
                    message: CONFIG.messages.specialChars
                }
            } else {
                response.specialChars = {
                    validated: true
                }
            }
        }

        //checking strength score
        if ( CONFIG.requirements.strength ) {
            if ( scorePassword(password) < CONFIG.requirements.strength ) {
                response.strength = {
                    validated: false,
                    message: CONFIG.messages.strength,
                    score: scorePassword(password)
                }
            } else {
                response.strength = {
                    validated: true,
                    score: scorePassword(password)
                }
            }
        }

        return response;
    }


    function match(passwordA, passwordB) {
        if ( passwordA == passwordB ) {
            return true;
        }
    }





    //http://stackoverflow.com/a/11268104
    function scorePassword(password) {
        //score of 60 is considered good
        //score of 80 is considered strong
        var score = 0;
        if (!password)
            return score;

        // award every unique letter until 5 repetitions
        var letters = new Object();
        for (var i=0; i<password.length; i++) {
            letters[password[i]] = (letters[password[i]] || 0) + 1;
            score += 5.0 / letters[password[i]];
        }

        // bonus points for mixing it up
        var variations = {
            digits: /\d/.test(password),
            lower: /[a-z]/.test(password),
            upper: /[A-Z]/.test(password),
            nonWords: /\W/.test(password),
        }

        variationCount = 0;
        for (var check in variations) {
            variationCount += (variations[check] == true) ? 1 : 0;
        }
        score += (variationCount - 1) * 10;

        return parseInt(score);
    }


    function specialCharacters(password) {
        //based on http://stackoverflow.com/a/13840211
        if ( /^[a-zA-Z0-9-_]*$/.test(password) == false ) {
            return true;
        } else {
            return false;
        }
    }


    function containsNumber(password) {
        //based on http://stackoverflow.com/a/5778071
        var matches = password.match(/\d+/g);
        if ( matches != null ) {
            return true;
        } else {
            return false;
        }
    }


    function containsCapitalLetters(password) {
        //based on http://stackoverflow.com/a/2830852
        return password.toLowerCase() != password;
    }


    function maximumCharacters(password) {
        if ( password.length > CONFIG.requirements.max ) {
            return false;
        } else {
            return true;
        }
    }


    function minimumCharacters(password) {
        if ( password.length < CONFIG.requirements.min ) {
            return false;
        } else {
            return true;
        }
    }



    return {
        validate: validate,
        match: match
    }


}