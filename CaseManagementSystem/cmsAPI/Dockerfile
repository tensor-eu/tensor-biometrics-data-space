FROM node:18-alpine AS node
# Create app directory
WORKDIR /usr/src/app
# Install app dependencies
# A wildcard is used to ensure both package.json AND package-lock.json are copied
# where available (npm@5+)
COPY package*.json ./
COPY public ./public/
COPY libraries ./libraries
RUN npm install
# If you are building your code for production
#RUN npm ci --only=production
# Bundle app source
COPY src ./src/

EXPOSE ${API_PORT}
CMD npm start
