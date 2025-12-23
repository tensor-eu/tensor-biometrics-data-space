import KeycloakBearerStrategy from 'passport-keycloak-bearer'
import dotenv from 'dotenv'
dotenv.config({path: 'api.env'})

/**
 * 
 * @param {import('express').Request} req 
 * @param {import('express').Response} res 
 * @param {import('express').NextFunction} next 
 */
const internalAuth = (req, res, next) => {
    const internalToken = req.headers.authorization
    if (internalToken === ('InternalWS '+ process.env.INTERNAL_WS_TOKEN)){
        next()
    }
    else{
        return res.status(403).json({error: "Internal token mismatch"})
    }
}

/**
 * 
 * @param {import('passport')} passport 
 */
const authChecker = (passport) => {
    return (req, res, next) => {
        if(req.headers.authorization?.includes('InternalWS')){
            internalAuth(req, res, next)
        }else{
            return passport.authenticate("keycloak", { session: false })(req,res,next)
        }
    }
}

// JWT Strategy Configuration
const strategy = new KeycloakBearerStrategy({
    "realm": process.env.KEYCLOAK_REALM_NAME,
    "url": process.env.KEYCLOAK_BASE_URL,
    "algorithms": ['RS256'],
    "ignoreExpiration": false
})

export {internalAuth, authChecker, strategy}