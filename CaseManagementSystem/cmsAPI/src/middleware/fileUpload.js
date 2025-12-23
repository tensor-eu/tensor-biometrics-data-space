import multer from 'multer'
import caseService from '../services/caseService';
import fs from 'fs'
import path from 'path';

// Define storage for uploaded files
const storage = multer.diskStorage({
    destination: (req, file, cb) => {
        const businessKey = req.params.businessKey                    //read case id from request
        caseService.getCase(businessKey).then((currCase) =>{
          fs.mkdirSync('uploads/'+businessKey+'/', {recursive:true})    //create a dir in uploads/businessKey
          cb(null, 'uploads/'+businessKey+'/');                         // Destination folder for uploaded files
        })
    },
    filename: (req, file, cb) => {
      cb(null, file.originalname);
    },
  });

  const fileFilter = multer
  
  // Initialize Multer with the storage configuration and filter out multiple duplicate files
  const upload = multer({
    storage: storage,
    fileFilter:  (req, file, callback) => {
      if (fs.existsSync(path.join("uploads", req.params.businessKey, file.originalname))) {
          callback(new Error(`File ${file.originalname} is already uploaded in case ${req.params.businessKey}!`));
      } else {
          callback(null, true);
      }
    }
  })

export default upload