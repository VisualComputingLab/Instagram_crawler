# Instagram_crawler
A java web crawler for the Instagram’s social network API v.1. 

#About this project 

Project name: Instagram Crawler
Architecture: Restfull application
Programming language: java 
Structuring and output format: json
Application server: Apache Tomcat
Messaging system: RabbitMQ, based on the AMQP standard

A java wrapper for the Instagram’s social network API v.1. Instagram is based on a mobile application that allows users to interact and communicate with each other by sharing media.
With the InstagramCrawler we try to gather images and their surrounding information.
Every image and its relevant metadata form a separate message that is delivered to the RabbitMQ.
The process is initiated by posting (POST request) a request to the Tomcat using a rest client (i.e. Advanced Rest Client for Google Chrome browser) followed by the .json file containing the request payload. The result of the request is written to the RabbitMQ and we get a server response about the operation status.

#Users --REST calls 

The user in order to search the Instagram social network for uploaded images over a specific topic has to post a request with a specific payload to indicate the search parameters. 

i.e.
POST http://localhost:8084/InstagramCrawler/resources/crawl
"Content-Type":"application/json"

Payload
{
"search_by": "tags/location",
"instagram": {
			"apiKey": "yourApiKey",
			"tag":"fashion",
			"max_results": 2000,
"lat":48.858093,
			"lng":2.294694

	
},
"rabbit": {
		"host": "localhost",
		"queue": "CRAWLER_NAME_QUEUE"
}
}

•	The url defines where the service runs
•	The content-type defines what type is the request payload we are about to send to the application server
•	search_by primitive:
o	Defines whether we want to search using a tag ‘tags’ (as inserted by the user that upload a photo), or whether we want to search for uploads in a specific location ‘location’ by lat and lng coordinates. 
•	instagram object:
o	API key primitive ‘apiKey’ is the client id provided to the developer when a new application gets registered that exploits the Instagram+ v1.
o	Tag primitive ‘tag’ is the parameter we want to search for. It is necessary only when ‘Tags’ is defined in the ‘Search_by’ primitive.
o	Max number of results primitive ‘max_results (optional), specifies the number of the results returned. If it does not exist in the instagram object the crawler will return all entries. Integer between 1 and 1000000.
o	Latitude and longitude ‘lat’ and ‘lng’ primitives define the coordinates of a location. They are necessary only when ‘location’ is defined in the ‘Search_by’ primitive. Both are double values
•	rabbit object:
o	Host primitive ‘host’ defines where the RabbitMQ server is hosted 
o	Queue primitive ‘queue’ defined how the queue that will hold the messages should be named.


Since the server returns a 200, OK message the json objects that have been created can be accessed through the RabbitMQ server platform (localhost:15672…guest,guest)
	 

#Developers 

Package: gr.iti.vcl.instagramcrawl.impl

InstagramCrawl.java methods documentation

The output of this class is zero or more messages written to a specific RabbitMQ queue containing information about images posted on Instagram as activities and a json object over the operation status.

parseOut

Responsible method for calling the initial requests to the Instagram API. Opens and closes connection, creates queues to the RabbitMQ service and writes multiple objects to the queues. Returns a json object that notifies the user over the operation status. The operation time depends on the amount of the results per tag returned from the API calls and from the maximum number of results defined by the user.

@param jsonObject 	The paylod of the initial POST request that the user provides and. defines the parameters to form the GET request to the Instagram API. 
@return 		The json object containing information about process status.
@throws IOException 	If an input or output exception occurred.
@throws Exception 		If an input or output exception occurred.

parseTagMedia

Responsible for passing the user defined parameters to the GET requests to the Instagram API to retrieve results over a specific tag and parse these results in order to form the json object that will be written to the RabbitMQ.

@param tag		The tag parameter of the request.
@param apikey_val		The Instagram Developer client id or access token.
@param stopper		Limits the results.
@param mediacount		Limits the results.
@param qName		Defines the name of the queue to write the messages.
@return 		An integer to help keep track of the results returned. 

parseLocationMedia 

Responsible for passing the user defined parameters to the GET requests to the Instagram API to retrieve results in a specific location and parse these results in order to form the json object that will be written to the RabbitMQ.

@param locid		The location id parameter of the request.
@param apikey_val		The Instagram Developer client id or access token.
@param stopper		Limits the results.
@param mediacount		Limits the results.
@param qName		Defines the name of the queue to write the messages.
@return 		An integer to help keep track of the results returned. 

callGET

Responsible for passing the GET request in a URL form to the Instargam+ API to retrieve tags and location ids.

@param url 		The url.
@return 		The string containing the GET response. 

convertStreamToString

Responsible for parsing the input stream created by the GET request to a String 

@param is 		The inputStream.
@return 		The String. 
@throws IOException 	If an input or output exception occurred.

writeToRMQ

Responsible for writing messages to the queue.

@param json 		The json object that will be stored to the messages queue (bytes).
@param qName		The qName that the message will be stored to.
@throws IOException 	If an input or output exception occurred.

openRMQ

Responsible for creating a connection with the RabbitMQ server and creating a queue 

@param host		The host of the RabitMQ.
@param qName		The qName that the message will be stored to.
@throws IOException 	If an input or output exception occurred.

closeRMQ

Responsible for closing the connection and channels to the RabbitMQ queue

log & err

Logging and error messaging methods

Package: gr.iti.vcl.instagramcrawl.rest

InstagramCrawl_Rest.java methods documentation

@POST
@Consumes("application/json")
@Produces("application/json")

postJson

The rest implementation for the Instagram crawler.
@param json 	The json object containing the payload for the Post request provided by the user.
@return json	The json object containing the operation status.
@throws Exception	if json object not provided to method 


#Problems met

Instagram API reference site is poorly documented as for the outcome and limitations of the requests (tags search results peculiarities as described bellow, results limited to half from that mentioned on media_count for tag/search).

Cannot return more than 4 people that like the photo. The total like count though is displayed.

On tags search, max results returned is 50 without next page token to iterate.

On tags search, if search term has exact match then we get it as result. To be specific, on tags search, if search term has exact match and is up to 4 letters then we get a single result. 
If no exact match is found we get maximum 50 possible hits containing the search term and their media count index! Additionally the search results returned contain the search term only as first part of the string, and not in the middle….Which of them to get?! 

Not possible to make an API call that returns images by both Tag and Location. Multiple requests have to be made and results have to be manually filtered.

#Future work

Check scattering in Media count difference among resulted tags, in order to get more tags instead of the first 5!

Use media/search to search for media. The default time span is set to 5 days. The time span must not exceed 7 days.

UI to get from map Latitude and Longitude of Point (integrate - use Google maps API).
UI to select whether the location id comes from Facebook places, Foursquare places or lat &lng

Attribution Field: If an app is taken with an external application and uploaded to Instagram (currently we only support Hipstamatic), then it will have an attribution element.
"attribution": {
	"website": "http:\/\/hipstamatic.com\/",
	"itunes_url": "http:\/\/itunes.apple.com\/us\/app\/hipstamatic\/id342115564?mt=8&partnerId=30&siteID=Yyddg65Y5dM-apimlSPw0fy54CQ20pccxQ",
	"name": "Hipstamatic"
}

Thread.sleep(700)warning on Netbeans, rewrite it using a executor service. --not a problem at this time! 

created_time convert it to date time from unix time!
