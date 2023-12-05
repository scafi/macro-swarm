// When the user clicks on the search box, we want to toggle the search dropdown
function displayToggleSearch(e) {
  e.preventDefault();
  e.stopPropagation();

  closeDropdownSearch(e);
  
  if (idx === null) {
    console.log("Building search index...");
    prepareIdxAndDocMap();
    console.log("Search index built.");
  }
  const dropdown = document.querySelector("#search-dropdown-content");
  if (dropdown) {
    if (!dropdown.classList.contains("show")) {
      dropdown.classList.add("show");
    }
    document.addEventListener("click", closeDropdownSearch);
    document.addEventListener("keydown", searchOnKeyDown);
    document.addEventListener("keyup", searchOnKeyUp);
  }
}

//We want to prepare the index only after clicking the search bar
var idx = null
const docMap = new Map()

function prepareIdxAndDocMap() {
  const docs = [  
    {
      "title": "Alchemist example",
      "url": "/macro-swarm/guide/alchemist.html",
      "content": "Requirements For running the examples, the simulations and the chart generation, you need to have the following installed: A valid JDK installation (&gt;= 11) A python installation (&gt;= 3.8) Running the examples The examples are located in the src/main/scala/examples folder. Each example has an associated yaml file, which contains the configuration for the Alchemist simulation. In the following we briefly describe how to run each example and what it is the expected result. For enabling the trace, follow the following video: Constant movement Example Description Command src/main/scala/examples/ConstantMovement A swarm of agents moving in a straight line `./gradlew runConstantMovementGraphic` Explore Example Description Command src/main/scala/examples/Explore A swarm of agents exploring a fixed area `./gradlew runConstantMovementGraphic` Obstacle Avoidance Example Description Command src/main/scala/examples/ObstacleAvoidance A swarm that try to avoid obstacles `./gradlew runObstacleAvoidanceBigGraphic` or `./gradlew runObstacleAvoidanceMiddleGraphic` or `./gradlew runObstacleAvoidanceGraphic` Towards Leader Example Description Command src/main/scala/examples/TowardsLeader Nodes go towards a sink point (a leader) `./gradlew runTowardsLeaderGraphic` Spin Around a Leader Example Description Command src/main/scala/examples/BranchingExample Nodes spin aroud a leader `./gradlew runSpinAroundGraphic` Reynolds Flocking Example Description Command src/main/scala/examples/ReynoldFlock Swarm moving following reynolds rule `./gradlew runReynoldFlockGraphic` Team Formation (branching) Example Description Command [src/main/scala/examples/BranchingExample](src/main/scala/examples/BranchingExample) Example of team formation through branch `./gradlew runBranchingExampleGraphic` Team Formation (logical) Example Description Command src/main/scala/examples/TeamFormation A swarm that create several sub-swarm based on spatial constraints `./gradlew runTeamFormationGraphic` Shape Formation Example Description Command src/main/scala/examples/AllShape A swarm of nodes that form several shapes `./gradlew runAllShapeGraphic` For other examples, please refer to the examples folder. For each file, you can run the corresponding example with the following command: ./gradlew run&lt;ExampleName&gt;Graphic Simulation: Find and Rescue The focus is on a scenario involving a fleet of drones patrolling a 1 km² area, designed to respond to emergency situations such as fires or injuries. In the following, we describe the main characteristics of the scenario, the simulation parameters, and the expected results. Scenario Details Environment: A spatial area of 1 km². Emergency Situations: These include events like fires or injuries, and are randomly generated within the simulation. Drone Fleet Composition: Explorers: 50 drones tasked with identifying emergency situations. Healers: 5 drones designated to respond to and resolve emergencies. Operation: Exploration is conducted in groups, each consisting of at least one healer and multiple explorers. Drone Specifications: Speed: Maximum of 20 km/h. Communication Range: 100 meters. Simulation Parameters: Duration: Each run lasts 90 minutes. Emergency Generation: Emergencies occur randomly in a [0, 50] minutes timeframe. Objective: Minimize the number of unresolved emergency situations. Running the Simulation To observe the dynamics of the simulation, execute the following command: ./gradlew runRescueGraphic In the following there is a sequence of screenshots of the simulation: Further Information For a comprehensive understanding and additional details, please refer to the accompanying academic paper. Reproduce the results To reproduce the results of the paper, you can run the following command: ./gradlew runRescueBatch This will launch 64 simulations with different seeds. Each of them, will produce a csv file in the data folder. In this repository, the data is already loaded, so you can directly run the following command to generate the plots: pip install -r requirements.txt python process.py This will produce the following charts stored in charts/:"
    } ,    
    {
      "title": "Main concepts",
      "url": "/macro-swarm/guide/concepts.html",
      "content": "MacroSwarm is based on the notion of computational fields, which are functions that map a position in space to a value. In particular, In this case each collective movement is expressed as a computational field, which is a function that maps a position in space to a velocity vector. These fields can be composed to create more complex behaviors. Thus, in this library, we collect several main basic behaviors that can be composed to create more complex behaviors. This structure is exemplified in the following figure: In the following, we describe the main function exposed by the library."
    } ,    
    {
      "title": "Home",
      "url": "/macro-swarm/",
      "content": "Macro Swarm is a field-based libraries for expressing swarm behaviors in a declarative way. It is built on top of the Aggregate computing framework, which is a distributed computing framework for the JVM. Particularly, it is built of top of ScaFi, a scala library for programming aggregate computing systems."
    } ,      
    {
      "title": "Quick start",
      "url": "/macro-swarm/guide/quick.html",
      "content": "For more details, please refer to the API documentation. MacroSwarm is a field-based libraries for expressing swarm behaviors in a declarative way. It is based on ScaFi, a scala library for programming aggregate computing systems. It supports a large variety of swarm behaviors, including collective movement, shape formation, team formation, and collective planning. More details about the library can be found in the API documentation and in the main concepts section of the guide. This library does not provide any simulation environment, but it is possible to use it with Alchemist. In the alchemist section of the guide, we provide a quick tutorial on how to use MacroSwarm with Alchemist, presented in a scientific paper at COORDINATION 2023."
    } ,      
  ];

  idx = lunr(function () {
    this.ref("title");
    this.field("content");

    docs.forEach(function (doc) {
      this.add(doc);
    }, this);
  });

  docs.forEach(function (doc) {
    docMap.set(doc.title, doc.url);
  });
}

// The onkeypress handler for search functionality
function searchOnKeyDown(e) {
  const keyCode = e.keyCode;
  const parent = e.target.parentElement;
  const isSearchBar = e.target.id === "search-bar";
  const isSearchResult = parent ? parent.id.startsWith("result-") : false;
  const isSearchBarOrResult = isSearchBar || isSearchResult;

  if (keyCode === 40 && isSearchBarOrResult) {
    // On 'down', try to navigate down the search results
    e.preventDefault();
    e.stopPropagation();
    selectDown(e);
  } else if (keyCode === 38 && isSearchBarOrResult) {
    // On 'up', try to navigate up the search results
    e.preventDefault();
    e.stopPropagation();
    selectUp(e);
  } else if (keyCode === 27 && isSearchBarOrResult) {
    // On 'ESC', close the search dropdown
    e.preventDefault();
    e.stopPropagation();
    closeDropdownSearch(e);
  }
}

// Search is only done on key-up so that the search terms are properly propagated
function searchOnKeyUp(e) {
  // Filter out up, down, esc keys
  const keyCode = e.keyCode;
  const cannotBe = [40, 38, 27];
  const isSearchBar = e.target.id === "search-bar";
  const keyIsNotWrong = !cannotBe.includes(keyCode);
  if (isSearchBar && keyIsNotWrong) {
    // Try to run a search
    runSearch(e);
  }
}

// Move the cursor up the search list
function selectUp(e) {
  if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index) && (index > 0)) {
      const nextIndexStr = "result-" + (index - 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Move the cursor down the search list
function selectDown(e) {
  if (e.target.id === "search-bar") {
    const firstResult = document.querySelector("li[id$='result-0']");
    if (firstResult) {
      firstResult.firstChild.focus();
    }
  } else if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index)) {
      const nextIndexStr = "result-" + (index + 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Search for whatever the user has typed so far
function runSearch(e) {
  if (e.target.value === "") {
    // On empty string, remove all search results
    // Otherwise this may show all results as everything is a "match"
    applySearchResults([]);
  } else {
    const tokens = e.target.value.split(" ");
    const moddedTokens = tokens.map(function (token) {
      // "*" + token + "*"
      return token;
    })
    const searchTerm = moddedTokens.join(" ");
    const searchResults = idx.search(searchTerm);
    const mapResults = searchResults.map(function (result) {
      const resultUrl = docMap.get(result.ref);
      return { name: result.ref, url: resultUrl };
    })

    applySearchResults(mapResults);
  }

}

// After a search, modify the search dropdown to contain the search results
function applySearchResults(results) {
  const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
  if (dropdown) {
    //Remove each child
    while (dropdown.firstChild) {
      dropdown.removeChild(dropdown.firstChild);
    }

    //Add each result as an element in the list
    results.forEach(function (result, i) {
      const elem = document.createElement("li");
      elem.setAttribute("class", "dropdown-item");
      elem.setAttribute("id", "result-" + i);

      const elemLink = document.createElement("a");
      elemLink.setAttribute("title", result.name);
      elemLink.setAttribute("href", result.url);
      elemLink.setAttribute("class", "dropdown-item-link");

      const elemLinkText = document.createElement("span");
      elemLinkText.setAttribute("class", "dropdown-item-link-text");
      elemLinkText.innerHTML = result.name;

      elemLink.appendChild(elemLinkText);
      elem.appendChild(elemLink);
      dropdown.appendChild(elem);
    });
  }
}

// Close the dropdown if the user clicks (only) outside of it
function closeDropdownSearch(e) {
  // Check if where we're clicking is the search dropdown
  if (e.target.id !== "search-bar") {
    const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
    if (dropdown) {
      dropdown.classList.remove("show");
      document.documentElement.removeEventListener("click", closeDropdownSearch);
    }
  }
}
